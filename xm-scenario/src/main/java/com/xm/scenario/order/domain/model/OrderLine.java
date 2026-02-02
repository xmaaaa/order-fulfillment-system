package com.xm.scenario.order.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 订单行（实体，属于订单聚合内）
 */
public class OrderLine {
    private final String skuId;
    private final int quantity;
    private final BigDecimal price;

    public OrderLine(String skuId, int quantity, BigDecimal price) {
        if (skuId == null || skuId.isBlank() || quantity <= 0 || price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid OrderLine: skuId, quantity>0, price>=0 required");
        }
        this.skuId = skuId;
        this.quantity = quantity;
        this.price = price;
    }

    public String getSkuId() {
        return skuId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getAmount() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderLine orderLine = (OrderLine) o;
        return quantity == orderLine.quantity && Objects.equals(skuId, orderLine.skuId) && Objects.equals(price, orderLine.price);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skuId, quantity, price);
    }
}
