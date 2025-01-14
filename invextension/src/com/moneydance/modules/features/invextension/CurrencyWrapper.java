/*
 * CurrencyWrapper.java
 * Copyright (c) 2014, Dale K. Furrow
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.moneydance.modules.features.invextension;

import com.infinitekind.moneydance.model.CurrencySnapshot;
import com.infinitekind.moneydance.model.CurrencyType;

import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * Wrapper for CurrencyType
 * <p/>
 * Version 1.0
 *
 * @author Dale Furrow
 */

public final class CurrencyWrapper implements Aggregator {
    static String columnName = "Ticker";
    static String reportingName = "Ticker";
    CurrencyType currencyType;
    String curID = "";
    String ticker;
    boolean isCash = false; //true if Currency represents uninvested cash
    LinkedHashSet<SecurityAccountWrapper> secAccts;

    public CurrencyWrapper(CurrencyType currencyType) {

        this.currencyType = currencyType;
        this.secAccts = new LinkedHashSet<>();
        this.curID = this.currencyType.getParameter("id");
        this.ticker = getTickerSymbolFromCurrType();
    }

    /**
     * dummy constructor to provide values for composite reports
     *
     * @param ticker string input for column
     */
    public CurrencyWrapper(String ticker) {
        currencyType = null;
        this.ticker = ticker;
    }

    /**
     * Constructor for Aggregate
     */
    public CurrencyWrapper() {
    }

    @SuppressWarnings("unused")
    public LinkedHashSet<SecurityAccountWrapper> getSecAccts() {
        return secAccts;
    }

    private String getTickerSymbolFromCurrType() {
        if (currencyType.getTickerSymbol().isEmpty()) {
            return "NoTicker_" + curID;
        } else {
            return currencyType.getTickerSymbol().trim();
        }
    }

    public boolean isCash() {
        return isCash;
    }

    public void setCash() {
        isCash = true;
    }


    public String getCurID() {
        return curID;
    }

    public CurrencyType getCurrencyType() {
        return currencyType;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + curID.hashCode();
        return result;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CurrencyWrapper other = (CurrencyWrapper) obj;
        return Objects.equals(curID, other.curID);
    }


    @Override
    public String getAggregateName() {
        return this.ticker + " ";
    }

    @Override
    public String getAllTypesName() {
        return "Tickers-ALL";
    }

    @Override
    public String getColumnName() {
        return columnName;
    }

    @Override
    public String getReportingName() {
        return reportingName;
    }

    @Override
    public String getName() {
        if (currencyType != null) {
            return getTickerSymbolFromCurrType();
        } else {
            return ticker;
        }

    }

    public CurrencySnapshot getSnapshotForDate(int dateInt){
        return this.currencyType.getSnapshotForDate(dateInt);
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

}
