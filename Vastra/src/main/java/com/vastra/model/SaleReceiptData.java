package com.vastra.model;

import java.util.List;

public class SaleReceiptData {
    private String billNumber;
    private String billDate;
    private String billTime;
    private String cashierName;
    private String counterName;

    private List<CartItem> items;
    private int qtyTotal;
    private int itemCount;

    private double subtotal;
    private double tax;
    private double discount;
    private double total;

    private String paymentMode;
    private boolean credit;

    private String customerCode;
    private String customerName;
    private String customerPlace;
    private String customerPhone;
    private boolean hasCustomer;

    private double pointsOpening;
    private double pointsEarned;
    private double pointsRedeemed;
    private double pointsClosing;

    private List<TaxBreakupRow> taxBreakup;

    private String storeName;
    private String storeAddress;
    private String storeGstin;
    private String storePhone;
    private String receiptFooter;
    private String exchangePolicy;

    public String getBillNumber() { return billNumber; }
    public void setBillNumber(String billNumber) { this.billNumber = billNumber; }

    public String getBillDate() { return billDate; }
    public void setBillDate(String billDate) { this.billDate = billDate; }

    public String getBillTime() { return billTime; }
    public void setBillTime(String billTime) { this.billTime = billTime; }

    public String getCashierName() { return cashierName; }
    public void setCashierName(String cashierName) { this.cashierName = cashierName; }

    public String getCounterName() { return counterName; }
    public void setCounterName(String counterName) { this.counterName = counterName; }

    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }

    public int getQtyTotal() { return qtyTotal; }
    public void setQtyTotal(int qtyTotal) { this.qtyTotal = qtyTotal; }

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public double getTax() { return tax; }
    public void setTax(double tax) { this.tax = tax; }

    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public boolean isCredit() { return credit; }
    public void setCredit(boolean credit) { this.credit = credit; }

    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPlace() { return customerPlace; }
    public void setCustomerPlace(String customerPlace) { this.customerPlace = customerPlace; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public boolean isHasCustomer() { return hasCustomer; }
    public void setHasCustomer(boolean hasCustomer) { this.hasCustomer = hasCustomer; }

    public double getPointsOpening() { return pointsOpening; }
    public void setPointsOpening(double pointsOpening) { this.pointsOpening = pointsOpening; }

    public double getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(double pointsEarned) { this.pointsEarned = pointsEarned; }

    public double getPointsRedeemed() { return pointsRedeemed; }
    public void setPointsRedeemed(double pointsRedeemed) { this.pointsRedeemed = pointsRedeemed; }

    public double getPointsClosing() { return pointsClosing; }
    public void setPointsClosing(double pointsClosing) { this.pointsClosing = pointsClosing; }

    public List<TaxBreakupRow> getTaxBreakup() { return taxBreakup; }
    public void setTaxBreakup(List<TaxBreakupRow> taxBreakup) { this.taxBreakup = taxBreakup; }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public String getStoreAddress() { return storeAddress; }
    public void setStoreAddress(String storeAddress) { this.storeAddress = storeAddress; }

    public String getStoreGstin() { return storeGstin; }
    public void setStoreGstin(String storeGstin) { this.storeGstin = storeGstin; }

    public String getStorePhone() { return storePhone; }
    public void setStorePhone(String storePhone) { this.storePhone = storePhone; }

    public String getReceiptFooter() { return receiptFooter; }
    public void setReceiptFooter(String receiptFooter) { this.receiptFooter = receiptFooter; }

    public String getExchangePolicy() { return exchangePolicy; }
    public void setExchangePolicy(String exchangePolicy) { this.exchangePolicy = exchangePolicy; }
}