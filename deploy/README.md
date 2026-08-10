# Kubernetes 部署操作手册（本地 kind + Helm）

把订单系统部署到 Kubernetes 的完整流程。本地用 **kind** 起集群、**Helm** 打包，
所有概念和 YAML 到生产（EKS / ACK / TKE / 自建 kubeadm）**通用**，区别只是换个集群和镜像仓库。进入生产后，需要根据云厂商、网络、存储、安全、流量入口和运维体系调整配置。

> 前提：命令默认在**项目根目录**执行（`order-fulfillment-system/`）。

---

## 0. 工具

| 工具 | 作用 | 安装 |
|---|---|---|
| `docker` | 构建镜像、给 kind 当"节点" | Docker Desktop |
| `kubectl` | 和集群对话的遥控器 | `brew install kubectl` |
| `kind` | 用 Docker 容器在本地造一个真 K8s 集群 | `brew install kind` |
| `helm` | K8s 应用打包/安装/升级/回滚 | `brew install helm` |

验证：`kind version && helm version && kubectl version --client`

---

## 1. 起集群

集群配置见 `deploy/kind-cluster.yaml`（1 控制面 + 2 工作节点）。

```bash
kind create cluster --name ofs --config deploy/kind-cluster.yaml

kubectl get nodes -o wide      # 3 个节点都 Ready
kubectl get pods -A            # 看 K8s 自身组件（apiserver/etcd/coredns/kindnet...）
```

删除集群（全部清干净）：`kind delete cluster --name ofs`

**集群结构速记**：带 `-control-plane` 的是"大脑"（只在控制面）；出现多份的（kindnet/kube-proxy）是"每节点一个"；`coredns` 提供集群内 DNS（Pod 靠 Service 名互访就靠它）。

---

## 2. 构建镜像并送进集群

kind 集群看不到你本机 Docker 的镜像，必须先 `kind load` 送进去。

```bash
docker build -t ofs-app:v1 .                    # ← 结尾的 . 不能少（构建上下文）
kind load docker-image ofs-app:v1 --name ofs    # 把镜像塞进 kind 各节点

# 验证镜像进了节点
docker exec ofs-worker crictl images | grep ofs-app
```

> **生产区别**：生产不用 `kind load`，而是 `docker push` 到镜像仓库（Harbor / ACR / ECR），
> 集群从仓库 `pull`。Deployment 里 `image:` 填"仓库地址/镜像名:标签"。

---

## 3. 两种部署方式

### 方式 A：裸 manifests（学习用，见 `deploy/k8s/`）

```bash
kubectl apply -f deploy/k8s/               # 一次应用整个目录
kubectl get deploy,svc,hpa,pdb -l app=ofs-app
kubectl delete -f deploy/k8s/              # 删除
```

文件说明：

| 文件 | 对象 | 作用 |
|---|---|---|
| `01-app-deployment.yaml` | Deployment | 声明"永远保持 N 个副本"，含探针/资源/滚动策略 |
| `02-app-service.yaml` | Service | 稳定访问入口 + 负载均衡（集群内用 DNS 名 `ofs-app` 访问） |
| `03-app-hpa.yaml` | HPA | 按 CPU 使用率自动增减副本 |
| `04-app-pdb.yaml` | PDB | 自愿中断（节点维护）时守住可用副本底线 |

### 方式 B：Helm（推荐/生产，见 `deploy/helm/ofs-app/`）

```bash
# 先删掉裸资源，避免同名冲突
kubectl delete -f deploy/k8s/ --ignore-not-found

# 装（dev 默认值）
helm install ofs deploy/helm/ofs-app

# 切生产配置（同一套模板 + 生产 values）
helm upgrade ofs deploy/helm/ofs-app -f deploy/helm/ofs-app/values-prod.yaml

# 版本历史 / 回滚
helm history ofs
helm rollback ofs 1          # 回到 REVISION 1（回滚本身也会生成新版本）

# 卸载
helm uninstall ofs
```

**渲染预览（不碰集群，强烈推荐改完先看）**：

```bash
helm lint deploy/helm/ofs-app
helm template ofs deploy/helm/ofs-app                                   # 用默认值渲染
helm template ofs deploy/helm/ofs-app -f deploy/helm/ofs-app/values-prod.yaml   # 用生产值渲染
```

Chart 结构：

```
deploy/helm/ofs-app/
├── Chart.yaml            # Chart 身份证（名字/版本）
├── values.yaml           # 默认(dev)值：副本数/镜像/资源/HPA/PDB/env
├── values-prod.yaml      # 生产差异值（只写和默认不同的字段）
└── templates/
    ├── _helpers.tpl      # 公共标签片段
    ├── configmap.yaml    # 把 values.env 渲染成配置注入容器（配置与镜像分离）
    ├── deployment.yaml   # = 裸 Deployment 的模板版
    ├── service.yaml
    ├── hpa.yaml          # 用 {{- if .Values.hpa.enabled }} 开关控制是否渲染
    └── pdb.yaml
```

**多环境核心**：模板不变，`-f 不同 values` 覆盖 → 渲染出不同环境的部署。

---

## 4. 关键运维命令速查

```bash
# 访问集群内服务（开发调试隧道）
kubectl port-forward svc/ofs-app 8888:8888
curl -s http://localhost:8888/actuator/health          # {"status":"UP"}

# 看日志 / 审问一个 Pod（最常用的排障两件套）
kubectl logs -l app=ofs-app --tail=50
kubectl describe pod -l app=ofs-app

# 实时看 Pod 状态
kubectl get pods -l app=ofs-app -w

# 看资源用量（需 metrics-server）
kubectl top pods -l app=ofs-app

# 滚动升级（裸 Deployment）
kubectl set image deployment/ofs-app ofs-app=ofs-app:v2
kubectl rollout status deployment/ofs-app
kubectl rollout history deployment/ofs-app
kubectl rollout undo deployment/ofs-app                # 回滚上一版
```

---

## 5. HPA 前置：装 metrics-server

HPA 需要 metrics-server 提供 CPU 用量。kind 默认不带，需手动装 + 打补丁。

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# kind 专用补丁：跳过 kubelet TLS 校验（仅本地/测试！生产不要）
kubectl patch -n kube-system deployment metrics-server --type=json \
  -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'

kubectl rollout status deployment metrics-server -n kube-system --timeout=120s
kubectl top pods -l app=ofs-app     # 能出数字 = 通了
```

演示扩容（造压力）：

```bash
# 起一个打流量的 Pod（集群内用 Service 名 ofs-app 访问，开 10 个并发循环）
kubectl run load-gen --image=busybox:1.36 --restart=Never -- /bin/sh -c \
  "for i in \$(seq 1 10); do (while true; do wget -q -O /dev/null http://ofs-app:8888/actuator/health; done) & done; wait"

# 另开窗口看副本自动上涨
kubectl get hpa ofs-app -w

# 停止压力
kubectl delete pod load-gen
```

---

## 6. 踩过的坑（血泪速记）

| 现象 | 原因 | 解法 |
|---|---|---|
| `docker build` 报 "requires exactly 1 argument" | 命令结尾少了 `.` | 补上 `.`（构建上下文） |
| Pod `ImagePullBackOff` | kind 看不到本机镜像 / 去远程拉 | `kind load` + Deployment 里 `imagePullPolicy: IfNotPresent` |
| Pod `Running` 但其实没就绪 | 没配探针，K8s 只看进程活没活 | 配 readiness/liveness/startup 探针（接 Actuator） |
| `kubectl top` 报错 / HPA `TARGETS <unknown>` | 没装 metrics-server | 装 + kind 补丁 `--kubelet-insecure-tls`，等 15-30s |
| Helm lint `unexpected EOF` | **注释里写了 `{{ if }}` 模板语法**，被当真解析 | 注释里别放花括号模板语法 |
| 回滚后副本没马上缩回 | HPA 缩容默认有 5 分钟稳定窗口（防抖动） | 等几分钟自然收敛，或配 `behavior.scaleDown.stabilizationWindowSeconds` |
| 生产值 5 副本部分 `Pending` | 本机 CPU 不够（`requests` 决定调度） | `kubectl describe pod` 看 `Insufficient cpu`；本地属正常 |

---

## 7. 探针与 Actuator 对应关系

应用是 Spring Boot，跑在 K8s 里自动暴露探针端点：

| 探针 | 端点 | 失败后果 |
|---|---|---|
| startupProbe | `/actuator/health/readiness` | 只在启动期用，给慢启动足够时间 |
| livenessProbe | `/actuator/health/liveness` | 判为卡死 → **重启容器** |
| readinessProbe | `/actuator/health/readiness` | 判为未就绪 → **从 Service 摘除**（不重启） |

---

## 8. 从 kind 到生产（迁移清单）

在 kind 上跑通后，上生产（EKS/ACK/TKE/kubeadm）只需换几处：

1. **集群**：不用 `kind create`，改用云控制台/Terraform 开托管集群，拿到 `kubeconfig`。
2. **镜像**：`kind load` → `docker push` 到镜像仓库；Helm `values` 里 `image.repository` 改成仓库地址。
3. **metrics-server**：生产集群一般已内置或一键开启，无需 `--kubelet-insecure-tls` 补丁。
4. **对外访问**：`ClusterIP` + `port-forward` → 加 `Ingress`/`LoadBalancer`。
```
