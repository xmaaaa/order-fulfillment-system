package com.xm.service.pay;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.*;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author XM
 * @date 2025/11/26
 */
@RestController
public class StripeDemo {

    private static final String SECRET_KEY = "11";
    private static final String PUBLISHABLE_KEY = "11";

    @PostConstruct
    public void init() {
        Stripe.apiKey = SECRET_KEY;
    }

    // ========== 1. Customer 相关 ==========

    /**
     * 创建客户
     */
    @PostMapping("/customer/create")
    public Map<String, Object> createCustomer(@RequestBody Map<String, String> data) {
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setName(data.get("name"))
                    .setEmail(data.get("email"))
                    .setPhone(data.getOrDefault("phone", ""))
                    .putMetadata("user_id", "123") // 关联你数据库的用户ID
                    .build();

            Customer customer = Customer.create(params);

            Map<String, Object> response = new HashMap<>();
            response.put("customerId", customer.getId());
            response.put("name", customer.getName());
            response.put("email", customer.getEmail());

            System.out.println("✅ 创建客户成功: " + customer.getId());
            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 查询客户信息
     */
    @GetMapping("/customer/{customerId}")
    public Map<String, Object> getCustomer(@PathVariable String customerId) {
        try {
            Customer customer = Customer.retrieve(customerId);

            Map<String, Object> response = new HashMap<>();
            response.put("id", customer.getId());
            response.put("name", customer.getName());
            response.put("email", customer.getEmail());
            response.put("balance", customer.getBalance()); // 账户余额

            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 2. Connected Account 相关 ==========

    /**
     * 创建商家账户（Connected Account）
     */
    @PostMapping("/account/create")
    public Map<String, Object> createAccount(@RequestBody Map<String, String> data) {
        try {
            AccountCreateParams params = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS) // Express 类型最简单
                    .setCountry("US")
                    .setEmail(data.get("email"))
                    .setCapabilities(
                            AccountCreateParams.Capabilities.builder()
                                    .setCardPayments(
                                            AccountCreateParams.Capabilities.CardPayments.builder()
                                                    .setRequested(true)
                                                    .build()
                                    )
                                    .setTransfers(
                                            AccountCreateParams.Capabilities.Transfers.builder()
                                                    .setRequested(true)
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            Account account = Account.create(params);

            Map<String, Object> response = new HashMap<>();
            response.put("accountId", account.getId());
            response.put("email", account.getEmail());
            response.put("type", account.getType());

            System.out.println("✅ 创建商家账户成功: " + account.getId());
            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建商家入驻链接（Onboarding）
     */
    @PostMapping("/account/onboarding")
    public Map<String, String> createAccountLink(@RequestBody Map<String, String> data) {
        try {
            AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                    .setAccount(data.get("accountId"))
                    .setRefreshUrl("http://localhost:8080/reauth")
                    .setReturnUrl("http://localhost:8080/return")
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build();

            AccountLink link = AccountLink.create(params);

            Map<String, String> response = new HashMap<>();
            response.put("url", link.getUrl());

            System.out.println("✅ 入驻链接: " + link.getUrl());
            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 3. Payment 相关 ==========

    /**
     * 创建支付（带 Customer 和 Connected Account）
     */
    @PostMapping("/payment/create")
    public Map<String, Object> createPayment(@RequestBody Map<String, Object> data) {
        try {
            Long amount = ((Number) data.get("amount")).longValue() * 100; // 转为分
            Long platformFee = amount * 20 / 100; // 平台抽成 20%

            PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                    .setAmount(amount)
                    .setCurrency("usd")
                    // ✅ 手动指定支持的支付方式
                    .addPaymentMethodType("card")           // 信用卡/借记卡
                    .addPaymentMethodType("alipay")         // 支付宝
                    .addPaymentMethodType("wechat_pay")     // 微信支付
                    .addPaymentMethodType("us_bank_account") // 美国银行账户
                    .addPaymentMethodType("link")           // Stripe Link
                    .addPaymentMethodType("cashapp")        // Cash App
                    .addPaymentMethodType("affirm")         // Affirm（分期付款）
                    .addPaymentMethodType("afterpay_clearpay"); // Afterpay

            // 如果提供了 Customer ID
            if (data.containsKey("customerId")) {
                builder.setCustomer((String) data.get("customerId"));
            }

            // 如果提供了 Connected Account（多商家模式）
            if (data.containsKey("accountId")) {
                builder.setTransferData(
                        PaymentIntentCreateParams.TransferData.builder()
                                .setDestination((String) data.get("accountId"))
                                .build()
                );
                builder.setApplicationFeeAmount(platformFee); // 平台抽成
            }

            PaymentIntent intent = PaymentIntent.create(builder.build());

            Map<String, Object> response = new HashMap<>();
            response.put("paymentIntentId", intent.getId());
            response.put("clientSecret", intent.getClientSecret());
            response.put("publishableKey", PUBLISHABLE_KEY);
            response.put("amount", intent.getAmount());

            System.out.println("✅ 创建支付: " + intent.getId());
            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 4. Invoice 相关 ==========

    /**
     * 创建发票
     */
    @PostMapping("/invoice/create")
    public Map<String, Object> createInvoice(@RequestBody Map<String, Object> data) {
        try {
            String customerId = (String) data.get("customerId");
            Long amount = ((Number) data.get("amount")).longValue() * 100;

            // 1. 先创建发票（draft 状态）
            Invoice invoice = Invoice.create(
                    InvoiceCreateParams.builder()
                            .setCustomer(customerId)
                            .setAutoAdvance(true)
                            .build()
            );

            // 2. 创建 InvoiceItem 并绑定
            InvoiceItem.create(
                    InvoiceItemCreateParams.builder()
                            .setCustomer(customerId)
                            .setInvoice(invoice.getId())
                            .setAmount(amount)
                            .setCurrency("usd")
                            .setDescription((String) data.getOrDefault("description", "服务费用"))
                            .build()
            );

            // 3. 确认发票
            invoice = invoice.finalizeInvoice();

            Map<String, Object> response = new HashMap<>();
            response.put("invoiceId", invoice.getId());
            response.put("invoiceUrl", invoice.getHostedInvoiceUrl());
            response.put("status", invoice.getStatus());
            response.put("amountDue", invoice.getAmountDue());

            System.out.println("✅ 创建发票: " + invoice.getId());
            System.out.println("📄 金额: $" + invoice.getAmountDue() / 100.0);
            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 查询发票状态
     */
    @GetMapping("/invoice/{invoiceId}")
    public Map<String, Object> getInvoice(@PathVariable String invoiceId) {
        try {
            Invoice invoice = Invoice.retrieve(invoiceId);

            Map<String, Object> response = new HashMap<>();
            response.put("id", invoice.getId());
            response.put("status", invoice.getStatus()); // draft, open, paid, void
            response.put("amountDue", invoice.getAmountDue());
            response.put("paid", invoice.getAmountPaid());

            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 5. 综合测试接口 ==========

    /**
     * 完整流程测试：创建客户 + 商家 + 支付
     */
    @PostMapping("/test/full-flow")
    public Map<String, Object> testFullFlow() {
        try {
            Map<String, Object> result = new HashMap<>();

            // 1. 创建客户
            Customer customer = Customer.create(
                    CustomerCreateParams.builder()
                            .setName("测试用户")
                            .setEmail("test@example.com")
                            .build()
            );
            result.put("customer", customer.getId());
            System.out.println("1️⃣ 创建客户: " + customer.getId());

            // 2. 创建商家账户
            Account account = Account.create(
                    AccountCreateParams.builder()
                            .setType(AccountCreateParams.Type.EXPRESS)
                            .setCountry("US")
                            .setEmail("merchant@example.com")
                            .setCapabilities(
                                    AccountCreateParams.Capabilities.builder()
                                            .setCardPayments(
                                                    AccountCreateParams.Capabilities.CardPayments.builder()
                                                            .setRequested(true)
                                                            .build()
                                            )
                                            .setTransfers(
                                                    AccountCreateParams.Capabilities.Transfers.builder()
                                                            .setRequested(true)
                                                            .build()
                                            )
                                            .build()
                            )
                            .build()
            );
            result.put("account", account.getId());
            System.out.println("2️⃣ 创建商家: " + account.getId());

            // 3. 创建支付（带分账）
            Long amount = 10000L; // $100
            Long platformFee = 2000L; // $20

            PaymentIntent intent = PaymentIntent.create(
                    PaymentIntentCreateParams.builder()
                            .setAmount(amount)
                            .setCurrency("usd")
                            .setCustomer(customer.getId())
                            .setTransferData(
                                    PaymentIntentCreateParams.TransferData.builder()
                                            .setDestination(account.getId())
                                            .build()
                            )
                            .setApplicationFeeAmount(platformFee)
                            .setAutomaticPaymentMethods(
                                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                            .setEnabled(true)
                                            .build()
                            )
                            .build()
            );
            result.put("paymentIntent", intent.getId());
            result.put("clientSecret", intent.getClientSecret());
            result.put("publishableKey", PUBLISHABLE_KEY);
            System.out.println("3️⃣ 创建支付: " + intent.getId());

            // 4. 创建发票
            InvoiceItem.create(
                    InvoiceItemCreateParams.builder()
                            .setCustomer(customer.getId())
                            .setAmount(5000L) // $50
                            .setCurrency("usd")
                            .setDescription("月度会员费")
                            .build()
            );

            Invoice invoice = Invoice.create(
                    InvoiceCreateParams.builder()
                            .setCustomer(customer.getId())
                            .build()
            );
            invoice = invoice.finalizeInvoice();
            result.put("invoice", invoice.getId());
            result.put("invoiceUrl", invoice.getHostedInvoiceUrl());
            System.out.println("4️⃣ 创建发票: " + invoice.getId());

            System.out.println("\n✅ 完整流程测试成功！");
            return result;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

}