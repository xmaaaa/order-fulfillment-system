{{/*
_helpers.tpl：定义可复用的模板片段（不直接生成资源，供其它模板 include）。
这里定义一组统一的标签，让所有资源都带上，便于筛选和管理。
*/}}
{{- define "ofs-app.labels" -}}
app: {{ .Chart.Name }}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}
