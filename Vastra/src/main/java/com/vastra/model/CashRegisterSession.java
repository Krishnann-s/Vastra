package com.vastra.model;

/** One day's cash drawer session - opened with a float, closed with a physical count. */
public class CashRegisterSession {
    private String id;
    private String businessDate;
    private int openingCents;
    private String openingNotes;
    private String openedBy;
    private String openedAt;
    private Integer closingCents;   // null until closed
    private String closingNotes;
    private String closedBy;
    private String closedAt;
    private Integer expectedCents;  // null until closed
    private Integer varianceCents;  // null until closed
    private String status;          // "OPEN" or "CLOSED"

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBusinessDate() { return businessDate; }
    public void setBusinessDate(String businessDate) { this.businessDate = businessDate; }

    public int getOpeningCents() { return openingCents; }
    public void setOpeningCents(int openingCents) { this.openingCents = openingCents; }
    public double getOpening() { return openingCents / 100.0; }

    public String getOpeningNotes() { return openingNotes; }
    public void setOpeningNotes(String openingNotes) { this.openingNotes = openingNotes; }

    public String getOpenedBy() { return openedBy; }
    public void setOpenedBy(String openedBy) { this.openedBy = openedBy; }

    public String getOpenedAt() { return openedAt; }
    public void setOpenedAt(String openedAt) { this.openedAt = openedAt; }

    public Integer getClosingCents() { return closingCents; }
    public void setClosingCents(Integer closingCents) { this.closingCents = closingCents; }
    public Double getClosing() { return closingCents != null ? closingCents / 100.0 : null; }

    public String getClosingNotes() { return closingNotes; }
    public void setClosingNotes(String closingNotes) { this.closingNotes = closingNotes; }

    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }

    public String getClosedAt() { return closedAt; }
    public void setClosedAt(String closedAt) { this.closedAt = closedAt; }

    public Integer getExpectedCents() { return expectedCents; }
    public void setExpectedCents(Integer expectedCents) { this.expectedCents = expectedCents; }
    public Double getExpected() { return expectedCents != null ? expectedCents / 100.0 : null; }

    public Integer getVarianceCents() { return varianceCents; }
    public void setVarianceCents(Integer varianceCents) { this.varianceCents = varianceCents; }
    public Double getVariance() { return varianceCents != null ? varianceCents / 100.0 : null; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isOpen() { return "OPEN".equals(status); }
}