package com.xm.service.pay;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.*;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
public class BeautySaasDemo {

    private static final String SECRET_KEY = "11";
    private static final String PUBLISHABLE_KEY = "11";

    // ========== 1. Product & Price（服务目录）==========

    /**
     * 创建服务项目
     */
    @PostMapping("/product/create")
    public Map<String, Object> createProduct(@RequestBody Map<String, Object> data) {
        try {
            // 1. 创建 Product（服务）
            ProductCreateParams productParams = ProductCreateParams.builder()
                .setName((String) data.get("name"))  // "剪发"
                .setDescription((String) data.getOrDefault("description", ""))
                .setType(ProductCreateParams.Type.SERVICE)  // 服务类型
                .putMetadata("category", (String) data.getOrDefault("category", "haircut"))
                .build();

            Product product = Product.create(productParams);

            // 2. 创建 Price（价格）
            Long amount = ((Number) data.get("price")).longValue() * 100;

            PriceCreateParams priceParams = PriceCreateParams.builder()
                .setProduct(product.getId())
                .setUnitAmount(amount)  // $30 = 3000 cents
                .setCurrency("usd")
                .build();

            Price price = Price.create(priceParams);

            Map<String, Object> response = new HashMap<>();
            response.put("productId", product.getId());
            response.put("priceId", price.getId());
            response.put("name", product.getName());
            response.put("price", price.getUnitAmount() / 100.0);

            System.out.println("✅ 创建服务: " + product.getName() + " - $" + price.getUnitAmount() / 100.0);
            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 列出所有服务
     */
    @GetMapping("/products")
    public Map<String, Object> listProducts() {
        try {
            ProductListParams params = ProductListParams.builder()
                .setActive(true)
                .setLimit(100L)
                .build();

            ProductCollection products = Product.list(params);

            List<Map<String, Object>> productList = new ArrayList<>();
            for (Product product : products.getData()) {
                // 获取该产品的价格
                PriceListParams priceParams = PriceListParams.builder()
                    .setProduct(product.getId())
                    .build();

                PriceCollection prices = Price.list(priceParams);

                Map<String, Object> item = new HashMap<>();
                item.put("id", product.getId());
                item.put("name", product.getName());
                item.put("description", product.getDescription());

                if (!prices.getData().isEmpty()) {
                    Price price = prices.getData().get(0);
                    item.put("priceId", price.getId());
                    item.put("price", price.getUnitAmount() / 100.0);
                }

                productList.add(item);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("products", productList);
            response.put("count", productList.size());

            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 2. Subscription（会员订阅）==========

    /**
     * 创建订阅会员
     */
    @PostMapping("/subscription/create")
    public Map<String, Object> createSubscription(@RequestBody Map<String, Object> data) {
        try {
            String customerId = (String) data.get("customerId");
            String priceId = (String) data.get("priceId");  // 月度会员的 Price ID

            SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(
                    SubscriptionCreateParams.Item.builder()
                        .setPrice(priceId)
                        .build()
                )
                // 如果有商家，设置分账
                .setApplicationFeePercent(BigDecimal.valueOf(20.0))  // 平台抽成 20%
                .setMetadata(
                    Map.of(
                        "type", "membership",
                        "merchant_id", data.getOrDefault("merchantId", "").toString()
                    )
                )
                .build();

            Subscription subscription = Subscription.create(params);

            Map<String, Object> response = new HashMap<>();
            response.put("subscriptionId", subscription.getId());
            response.put("status", subscription.getStatus());
            response.put("currentPeriodEnd", subscription.getCancelAt());

            System.out.println("✅ 创建订阅: " + subscription.getId());
            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 取消订阅
     */
    @PostMapping("/subscription/cancel")
    public Map<String, Object> cancelSubscription(@RequestBody Map<String, String> data) {
        try {
            String subscriptionId = data.get("subscriptionId");

            Subscription subscription = Subscription.retrieve(subscriptionId);
            subscription = subscription.cancel();

            Map<String, Object> response = new HashMap<>();
            response.put("subscriptionId", subscription.getId());
            response.put("status", subscription.getStatus());  // "canceled"

            System.out.println("✅ 取消订阅: " + subscriptionId);
            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 查询订阅状态
     */
    @GetMapping("/subscription/{subscriptionId}")
    public Map<String, Object> getSubscription(@PathVariable String subscriptionId) {
        try {
            Subscription subscription = Subscription.retrieve(subscriptionId);

            Map<String, Object> response = new HashMap<>();
            response.put("id", subscription.getId());
            response.put("status", subscription.getStatus());
            response.put("currentPeriodStart", subscription.getStartDate());
            response.put("currentPeriodEnd", subscription.getCancelAt());
            response.put("cancelAtPeriodEnd", subscription.getCancelAtPeriodEnd());

            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 3. Refund（退款）==========

    /**
     * 创建退款
     */
    @PostMapping("/refund/create")
    public Map<String, Object> createRefund(@RequestBody Map<String, Object> data) {
        try {
            String paymentIntentId = (String) data.get("paymentIntentId");

            RefundCreateParams.Builder builder = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId);

            // 部分退款 or 全额退款
            if (data.containsKey("amount")) {
                Long amount = ((Number) data.get("amount")).longValue() * 100;
                builder.setAmount(amount);
            }

            // 退款原因
            if (data.containsKey("reason")) {
                String reason = (String) data.get("reason");
                RefundCreateParams.Reason refundReason;
                switch (reason) {
                    case "duplicate":
                        refundReason = RefundCreateParams.Reason.DUPLICATE;
                        break;
                    case "fraudulent":
                        refundReason = RefundCreateParams.Reason.FRAUDULENT;
                        break;
                    default:
                        refundReason = RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
                }
                builder.setReason(refundReason);
            }

            Refund refund = Refund.create(builder.build());

            Map<String, Object> response = new HashMap<>();
            response.put("refundId", refund.getId());
            response.put("status", refund.getStatus());
            response.put("amount", refund.getAmount() / 100.0);
            response.put("reason", refund.getReason());

            System.out.println("✅ 创建退款: $" + refund.getAmount() / 100.0);
            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 查询退款状态
     */
    @GetMapping("/refund/{refundId}")
    public Map<String, Object> getRefund(@PathVariable String refundId) {
        try {
            Refund refund = Refund.retrieve(refundId);

            Map<String, Object> response = new HashMap<>();
            response.put("id", refund.getId());
            response.put("status", refund.getStatus());
            response.put("amount", refund.getAmount() / 100.0);
            response.put("reason", refund.getReason());
            response.put("created", refund.getCreated());

            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 4. Coupon（优惠券）==========

    /**
     * 创建优惠券
     */
    @PostMapping("/coupon/create")
    public Map<String, Object> createCoupon(@RequestBody Map<String, Object> data) {
        try {
            CouponCreateParams.Builder builder = CouponCreateParams.builder()
                .setName((String) data.get("name"))
                .setCurrency("usd");

            // 百分比折扣 or 固定金额折扣
            if (data.containsKey("percentOff")) {
                builder.setPercentOff(
                    (BigDecimal.valueOf(((Number) data.get("percentOff")).doubleValue())
                ));
            } else if (data.containsKey("amountOff")) {
                Long amount = ((Number) data.get("amountOff")).longValue() * 100;
                builder.setAmountOff(amount);
            }

            // 有效期
            if (data.containsKey("duration")) {
                String duration = (String) data.get("duration");
                switch (duration) {
                    case "once":
                        builder.setDuration(CouponCreateParams.Duration.ONCE);
                        break;
                    case "forever":
                        builder.setDuration(CouponCreateParams.Duration.FOREVER);
                        break;
                    case "repeating":
                        builder.setDuration(CouponCreateParams.Duration.REPEATING);
                        if (data.containsKey("durationInMonths")) {
                            builder.setDurationInMonths(
                                ((Number) data.get("durationInMonths")).longValue()
                            );
                        }
                        break;
                }
            }

            Coupon coupon = Coupon.create(builder.build());

            Map<String, Object> response = new HashMap<>();
            response.put("couponId", coupon.getId());
            response.put("name", coupon.getName());
            response.put("percentOff", coupon.getPercentOff());
            response.put("amountOff", coupon.getAmountOff() != null ? coupon.getAmountOff() / 100.0 : null);

            System.out.println("✅ 创建优惠券: " + coupon.getName());
            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 5. Payment Method（支付方式管理）==========

    /**
     * 保存支付方式
     */
    @PostMapping("/payment-method/attach")
    public Map<String, Object> attachPaymentMethod(@RequestBody Map<String, String> data) {
        try {
            String paymentMethodId = data.get("paymentMethodId");
            String customerId = data.get("customerId");

            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            paymentMethod = paymentMethod.attach(
                PaymentMethodAttachParams.builder()
                    .setCustomer(customerId)
                    .build()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("paymentMethodId", paymentMethod.getId());
            response.put("type", paymentMethod.getType());

            if (paymentMethod.getCard() != null) {
                response.put("brand", paymentMethod.getCard().getBrand());
                response.put("last4", paymentMethod.getCard().getLast4());
            }

            System.out.println("✅ 保存支付方式: " + paymentMethod.getId());
            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 列出客户的所有支付方式
     */
    @GetMapping("/payment-methods/{customerId}")
    public Map<String, Object> listPaymentMethods(@PathVariable String customerId) {
        try {
            PaymentMethodListParams params = PaymentMethodListParams.builder()
                .setCustomer(customerId)
                .setType(PaymentMethodListParams.Type.CARD)
                .build();

            PaymentMethodCollection paymentMethods = PaymentMethod.list(params);

            List<Map<String, Object>> methodList = new ArrayList<>();
            for (PaymentMethod pm : paymentMethods.getData()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", pm.getId());
                item.put("type", pm.getType());

                if (pm.getCard() != null) {
                    item.put("brand", pm.getCard().getBrand());
                    item.put("last4", pm.getCard().getLast4());
                    item.put("expMonth", pm.getCard().getExpMonth());
                    item.put("expYear", pm.getCard().getExpYear());
                }

                methodList.add(item);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("paymentMethods", methodList);
            response.put("count", methodList.size());

            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 6. Transfer（分账查询）==========

    /**
     * 查询分账详情
     */
    @GetMapping("/transfer/{transferId}")
    public Map<String, Object> getTransfer(@PathVariable String transferId) {
        try {
            Transfer transfer = Transfer.retrieve(transferId);

            Map<String, Object> response = new HashMap<>();
            response.put("id", transfer.getId());
            response.put("amount", transfer.getAmount() / 100.0);
            response.put("destination", transfer.getDestination());
            response.put("created", transfer.getCreated());
            response.put("description", transfer.getDescription());

            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 列出某个账户的所有转账
     */
    @GetMapping("/transfers")
    public Map<String, Object> listTransfers(@RequestParam(required = false) String destination) {
        try {
            TransferListParams.Builder builder = TransferListParams.builder()
                .setLimit(100L);

            if (destination != null) {
                builder.setDestination(destination);
            }

            TransferCollection transfers = Transfer.list(builder.build());

            List<Map<String, Object>> transferList = new ArrayList<>();
            for (Transfer transfer : transfers.getData()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", transfer.getId());
                item.put("amount", transfer.getAmount() / 100.0);
                item.put("destination", transfer.getDestination());
                item.put("created", transfer.getCreated());

                transferList.add(item);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("transfers", transferList);
            response.put("count", transferList.size());

            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 7. 预约定金（Deposit）==========

    /**
     * 创建预约定金支付
     */
    @PostMapping("/booking/deposit")
    public Map<String, Object> createBookingDeposit(@RequestBody Map<String, Object> data) {
        try {
            String customerId = (String) data.get("customerId");
            Long totalAmount = ((Number) data.get("totalAmount")).longValue() * 100;
            Long depositAmount = ((Number) data.get("depositAmount")).longValue() * 100;

            // 创建 Payment Intent（只收定金）
            PaymentIntent intent = PaymentIntent.create(
                PaymentIntentCreateParams.builder()
                    .setAmount(depositAmount)
                    .setCurrency("usd")
                    .setCustomer(customerId)
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)  // 手动扣款
                    .putMetadata("total_amount", totalAmount.toString())
                    .putMetadata("deposit_amount", depositAmount.toString())
                    .putMetadata("booking_type", "deposit")
                    .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                            .setEnabled(true)
                            .build()
                    )
                    .build()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("paymentIntentId", intent.getId());
            response.put("clientSecret", intent.getClientSecret());
            response.put("depositAmount", depositAmount / 100.0);
            response.put("totalAmount", totalAmount / 100.0);
            response.put("remainingAmount", (totalAmount - depositAmount) / 100.0);

            System.out.println("✅ 创建预约定金: $" + depositAmount / 100.0);
            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 到店后收取尾款
     */
    @PostMapping("/booking/final-payment")
    public Map<String, Object> captureFinalPayment(@RequestBody Map<String, Object> data) {
        try {
            String paymentIntentId = (String) data.get("paymentIntentId");

            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);

            // 扣除定金（授权转扣款）
            intent = intent.capture();

            // 计算尾款
            Long totalAmount = Long.parseLong(intent.getMetadata().get("total_amount"));
            Long depositAmount = intent.getAmount();
            Long remainingAmount = totalAmount - depositAmount;

            // 如果还有尾款，创建新的 Payment Intent
            PaymentIntent finalPayment = null;
            if (remainingAmount > 0) {
                finalPayment = PaymentIntent.create(
                    PaymentIntentCreateParams.builder()
                        .setAmount(remainingAmount)
                        .setCurrency("usd")
                        .setCustomer(intent.getCustomer())
                        .putMetadata("original_booking", paymentIntentId)
                        .putMetadata("payment_type", "final")
                        .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                        )
                        .build()
                );
            }

            Map<String, Object> response = new HashMap<>();
            response.put("depositCaptured", true);
            response.put("depositAmount", depositAmount / 100.0);
            response.put("remainingAmount", remainingAmount / 100.0);

            if (finalPayment != null) {
                response.put("finalPaymentId", finalPayment.getId());
                response.put("finalClientSecret", finalPayment.getClientSecret());
            }

            System.out.println("✅ 收取定金 + 尾款");
            return response;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== 8. 完整美业场景测试 ==========

    /**
     * 完整美业场景：发廊预约流程
     */
    @PostMapping("/test/salon-booking")
    public Map<String, Object> testSalonBooking() {
        try {
            Map<String, Object> result = new HashMap<>();

            // 1. 创建商家（发廊）
            Account salon = Account.create(
                AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setCountry("US")
                    .setEmail("salon@example.com")
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
            result.put("salonId", salon.getId());
            System.out.println("1️⃣ 创建发廊账户: " + salon.getId());

            // 2. 创建服务项目
            Product haircutProduct = Product.create(
                ProductCreateParams.builder()
                    .setName("剪发")
                    .setType(ProductCreateParams.Type.SERVICE)
                    .build()
            );

            Price haircutPrice = Price.create(
                PriceCreateParams.builder()
                    .setProduct(haircutProduct.getId())
                    .setUnitAmount(3000L)  // $30
                    .setCurrency("usd")
                    .build()
            );
            result.put("serviceId", haircutProduct.getId());
            result.put("priceId", haircutPrice.getId());
            System.out.println("2️⃣ 创建服务: 剪发 $30");

            // 3. 创建顾客
            Customer customer = Customer.create(
                CustomerCreateParams.builder()
                    .setName("顾客张三")
                    .setEmail("customer@example.com")
                    .build()
            );
            result.put("customerId", customer.getId());
            System.out.println("3️⃣ 创建顾客: " + customer.getId());

            // 4. 创建预约并支付（带分账）
            PaymentIntent booking = PaymentIntent.create(
                PaymentIntentCreateParams.builder()
                    .setAmount(3000L)
                    .setCurrency("usd")
                    .setCustomer(customer.getId())
                    .setTransferData(
                        PaymentIntentCreateParams.TransferData.builder()
                            .setDestination(salon.getId())  // 钱给发廊
                            .build()
                    )
                    .setApplicationFeeAmount(600L)  // 平台抽成 20%（$6）
                    .putMetadata("service", "剪发")
                    .putMetadata("salon", salon.getId())
                    .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                            .setEnabled(true)
                            .build()
                    )
                    .build()
            );
            result.put("bookingId", booking.getId());
            result.put("clientSecret", booking.getClientSecret());
            result.put("publishableKey", PUBLISHABLE_KEY);
            System.out.println("4️⃣ 创建预约支付: $30（发廊得 $24，平台得 $6）");

            // 5. 创建月度会员卡
            Product membership = Product.create(
                ProductCreateParams.builder()
                    .setName("月度VIP会员")
                    .setType(ProductCreateParams.Type.SERVICE)
                    .build()
            );

            Price membershipPrice = Price.create(
                PriceCreateParams.builder()
                    .setProduct(membership.getId())
                    .setUnitAmount(9900L)
                    .setCurrency("usd")
                    .setRecurring(
                        PriceCreateParams.Recurring.builder()
                            .setInterval(PriceCreateParams.Recurring.Interval.MONTH)
                            .build()
                    )
                    .build()
            );
            result.put("membershipPriceId", membershipPrice.getId());
            System.out.println("5️⃣ 创建会员卡: $99/月");

            System.out.println("\n✅ 完整美业场景创建成功！");
            return result;

        } catch (StripeException e) {
            throw new RuntimeException(e);
        }
    }

}