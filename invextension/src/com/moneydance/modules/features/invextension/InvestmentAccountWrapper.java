/*
 * InvestmentAccountWrapper.java
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


import com.infinitekind.moneydance.model.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

/**
 * Wrapper for Moneydance Class Investment Account, adds increased functionality
 * <p/>
 * Version 1.0
 *
 * @author Dale Furrow
 */

public final class InvestmentAccountWrapper implements Aggregator {
    // name of aggregation method
    static String reportingName = "Investment Account";
    // column name for sorting
    static String columnName = "InvAcct";
    //associated BulkSecInfo
    BulkSecInfo currentInfo;
    // associated Investment Account
    private Account investmentAccount;
    // Account Id
    private final String acctId;
    // associated CashAccount
    private SecurityAccountWrapper cashAccountWrapper;
    // Security Account Wrappers
    private ArrayList<SecurityAccountWrapper> securityAccountWrappers;
    private String name;

    public InvestmentAccountWrapper(Account invAcct, BulkSecInfo currentInfo) throws Exception {
        this.currentInfo = currentInfo;
        this.investmentAccount = invAcct;
        this.acctId = this.investmentAccount.getUUID();
        this.securityAccountWrappers = new ArrayList<>();
        this.name = Objects.requireNonNull(investmentAccount.getAccountName()).trim();
        //get Security Sub Accounts
        TreeSet<Account> subSecAccts = BulkSecInfo.getSelectedSubAccounts(invAcct,
                Account.AccountType.SECURITY);
        //Loop through Security Sub Accounts
        // FIXME: potential problem here if no tradeable securities defined for investment account
        for (Account subSecAcct : subSecAccts) {
            CurrencyWrapper currencyWrapper = getBulkSecInfo()
                    .getCurrencyWrappers().get(subSecAcct.getCurrencyType()
                            .getParameter("id"));
            TxnSet txnSet = getBulkSecInfo().getTransactionSet()
                    .getTransactionsForAccount(subSecAcct);
            GainsCalc gainsCalc = getBulkSecInfo().getGainsCalc();
            AccountBook accountBook = getBulkSecInfo().getAccountBook();
            //Load Security Account into Wrapper Class
            SecurityAccountWrapper secAcctWrapper = new SecurityAccountWrapper(subSecAcct,
                    currencyWrapper, txnSet,this,gainsCalc ,accountBook);
            // add Security Account to Investment Account
            this.securityAccountWrappers.add(secAcctWrapper);
        }
        createCashWrapper();  //creates basic cash wrapper
        this.securityAccountWrappers.add(cashAccountWrapper);   //add cash wrapper to total securityAccountWrappers
        createCashTransactions(); //populates cash wrapper with synthetic cash transactions
    }
    // constructors associated with aggregation, assign random accountid
    public InvestmentAccountWrapper() {
        this.acctId = UUID.randomUUID().toString();
    }

    public InvestmentAccountWrapper(String name) {
        this.name = name;
        this.acctId = UUID.randomUUID().toString();
    }

    /**
     * Populate Synthetic Cash Transactions for a given Investment Account
     *
     */
    public void createCashTransactions() throws Exception {
        // add to tempTransValues all Security and Account-Level Cash
        // transactions for this InvestmentAccountWrapper
        LinkedHashMap<String, TransactionValues> tempTransValues = this.getTransactionValues();
        LinkedHashMap<String, TransactionValues> cashTransactions = new LinkedHashMap<>();

        // add initial balance as a transValues object (use day before first
        // transaction date if available, creation date if not
        int firstDateInt = tempTransValues.isEmpty()
                ? DateUtils.getPrevBusinessDay(this.investmentAccount.getCreationDateInt())
                : DateUtils.getPrevBusinessDay(tempTransValues.firstEntry().getValue().getDateInt());
        TransactionValues initialTransactionValues = new TransactionValues(this, firstDateInt);
        String initialId = initialTransactionValues.getTxnID();
        cashTransactions.put(initialId, initialTransactionValues);

        // now there is guaranteed to be one transaction, so prevTransValues always exists
        for (Map.Entry<String, TransactionValues> entry : tempTransValues.entrySet()) {
            TransactionValues transactionValues = entry.getValue();
            TransactionValues prevCashTransValues = cashTransactions.lastEntry().getValue();
            // add synthetic cash transaction to overall cashTransactions set
            TransactionValues newTransactionValues = new TransactionValues(transactionValues,
                    prevCashTransValues, this);
            String parentId = newTransactionValues.getTxnID();
            cashTransactions.put(parentId, newTransactionValues);
            //Collections.sort(cashTransactions, TransactionValues.transComp);
        }

        this.cashAccountWrapper.setAllTransactionValues(cashTransactions);
    }

    /**
     * creates CashWrapper as a money market mutual fund
     *
     */
    private void createCashWrapper() throws Exception {
        LogController.logMessage(Level.FINE, String.format("Creating Cash Account for %s",
                this.getInvestmentAccount().getAccountName()));
        /* runaround to ensure backwards compatiblity */

        Account cashAccount;
        try {
            Class<AccountBook> clazz = AccountBook.class;
            Method method = clazz.getMethod("nullAccountBook");
            AccountBook nullAccountBook = (AccountBook) method.invoke(null);
            cashAccount = new Account(nullAccountBook);
        } catch (NoSuchMethodException e) {
            cashAccount = new Account(null);
        } catch (SecurityException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        /* end of runaround to ensure backwards compatiblity */

        cashAccount.setAccountName("CASH");
        cashAccount.setComment("New Security to hold cash transactions");
        cashAccount.setSecurityType(SecurityType.MUTUAL);
        cashAccount.setSecuritySubType("Money Market");
        cashAccount.setCurrencyType(currentInfo.getCashCurrencyWrapper().getCurrencyType());
        cashAccount.setParentAccount(this.investmentAccount);
        LogController.logMessage(Level.FINE, String.format("Cash account created: %s, %s, with currency type %s",
                cashAccount.getAccountName(), cashAccount.getUUID(), cashAccount.getCurrencyType().getUUID()));

        TxnSet txnSet = getBulkSecInfo().getTransactionSet()
                .getTransactionsForAccount(investmentAccount);
        GainsCalc gainsCalc = getBulkSecInfo().getGainsCalc();
        AccountBook accountBook = getBulkSecInfo().getAccountBook();
        this.cashAccountWrapper = new SecurityAccountWrapper(cashAccount,
                getCashCurrencyWrapper(), txnSet, this, gainsCalc, accountBook);
        currentInfo.getCashCurrencyWrapper().secAccts.add(this.cashAccountWrapper);
//        cashWrapper.generateTransValues();  Don't need to call this twice!
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + acctId.hashCode();
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
        InvestmentAccountWrapper other = (InvestmentAccountWrapper) obj;
        return acctId.equals(other.acctId);
    }

    public BulkSecInfo getBulkSecInfo() {
        return this.currentInfo;
    }

    public AccountBook getAccountBook(){
        return this.currentInfo.getAccountBook();
    }


    public ArrayList<SecurityAccountWrapper> getSecurityAccountWrappers() {
        return this.securityAccountWrappers;
    }


    public SecurityAccountWrapper getCashAccountWrapper() {
        return this.cashAccountWrapper;
    }

    public CurrencyWrapper getCashCurrencyWrapper() {
        return currentInfo.getCashCurrencyWrapper();

    }


    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    static <K, V> void orderByValue(
            LinkedHashMap<K, V> m, Comparator<? super V> c) {
        List<Map.Entry<K, V>> entries = new ArrayList<>(m.entrySet());
        m.clear();
        entries.stream()
                .sorted(Map.Entry.comparingByValue(c))
                .forEachOrdered(e -> m.put(e.getKey(), e.getValue()));
    }

    /**
     * Returns sorted transaction value lines for this investment account
     *
     * @return sorted transaction values list
     */
    public LinkedHashMap<String, TransactionValues> getTransactionValues() {
        LinkedHashMap<String, TransactionValues> outputTransactionValues = new LinkedHashMap<>();
        for (SecurityAccountWrapper securityAccountWrapper : securityAccountWrappers) {
            LinkedHashMap<String, TransactionValues> accountTransactionValues = securityAccountWrapper
                    .getTransactionValues();
            if (accountTransactionValues != null) {
                for (String thisId : accountTransactionValues.keySet()) {
                    TransactionValues transactionValues = accountTransactionValues.get(thisId);
                    outputTransactionValues.put(thisId, transactionValues);
                }
            }
        }
        orderByValue(outputTransactionValues, TransactionValues.transComp);
        return outputTransactionValues;
    }

    public ArrayList<String[]> listTransValuesInfo() {
        ArrayList<String[]> outputList = new ArrayList<>();
        for (SecurityAccountWrapper securityAccountWrapper : securityAccountWrappers) {
            outputList.addAll(securityAccountWrapper.listTransValuesInfo());
        }
        return outputList;
    }

    public CurrencyType getAccountCurrency(){
        return this.investmentAccount.getCurrencyType();
    }

    public double getAccountCurrencyUserRateByDateInt(int dateInt){
        return  this.getAccountCurrency().getRate(null, dateInt);
    }


    @Override
    public String getAggregateName() {
        return this.getName() + " ";
    }

    @Override
    public String getAllTypesName() {
        return "Accounts-ALL";
    }

    @Override
    public String getColumnName() {
        return columnName;
    }

    @Override
    public String getReportingName() {
        return reportingName;
    }

    public Account getInvestmentAccount() {
        return this.investmentAccount;
    }

}
