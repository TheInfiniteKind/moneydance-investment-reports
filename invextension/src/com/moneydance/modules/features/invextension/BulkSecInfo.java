/*
 * BulkSecInfo.java
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

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Retrieves maps which show relationships among accounts, + between accounts
 * and transactions Generates basic transaction data and balance sheet data for
 * further analysis
 *
 * @author Dale Furrow
 * @version 1.0
 * @since 1.0
 */

public final class BulkSecInfo {

    public static class ComparablePair<T extends Comparable<T>>{
        T c1;
        T c2;

        public ComparablePair(T c1, T c2) {
            this.c1 = c1;
            this.c2 = c2;
        }

        public int compare(){
            return c1.compareTo(c2);
        }
    }

    public static int compareAll(ComparablePair<? extends Comparable<?>>[] comparablePairs){
        for(ComparablePair<? extends Comparable<?>> comparablePair : comparablePairs){
            int compareVal = comparablePair.compare();
            if(compareVal != 0) return compareVal;
        }
        return comparablePairs[comparablePairs.length - 1].compare();
    }

    /**
     * Comparator to generate ordering of accounts based on type,
     * name and number
     */

    static Comparator<Account> acctComp = (a1, a2) -> {
        ComparablePair<? extends Comparable<?>>[] comparablePairs = new ComparablePair<?>[3];

        comparablePairs[0] = new ComparablePair<>(a1.getAccountType().code(), a2.getAccountType().code());
        comparablePairs[1] = new ComparablePair<>(a1.getAccountName(), a2.getAccountName());
        comparablePairs[2] = new ComparablePair<>(a1.getParameter("id"), a2.getParameter("id"));

        return compareAll(comparablePairs);
    };

    /**
     * Comparator sorts transaction by date, account number, a custom
     * ordering based on transaction type, finally by transaction ID
     */

    static Comparator<ParentTxn> txnComp = (t1, t2) -> {
        ComparablePair<? extends Comparable<?>>[] comparablePairs = new ComparablePair<?>[4];

        comparablePairs[0] = new ComparablePair<>(t1.getDateInt(), t2.getDateInt());
        comparablePairs[1] = new ComparablePair<>(getAssociatedAccount(t1).getParameter("id"),
                getAssociatedAccount(t1).getParameter("id"));
        comparablePairs[2] = new ComparablePair<>(getTxnSortOrder(InvestTxnType.values()[TxnUtil.getInvstTxnType(t1)]),
                getTxnSortOrder(InvestTxnType.values()[TxnUtil.getInvstTxnType(t2)]));
        comparablePairs[3] = new ComparablePair<>(t1.getParameter("id"), t2.getParameter("id"));

        return compareAll(comparablePairs);

    };

    /* Cash Currency Type for uninvested cash */
    private final CurrencyWrapper cashCurrencyWrapper;
    /* first transaction date (to price cash currency)*/
    private final int firstDateInt;
    /* HashSet of CurrencyWrappers */
    private final HashMap<String, CurrencyWrapper> currencyWrappers;
    /* all transactions in root */
    private final TransactionSet transactionSet;
    /*TreeMap of Transvalues for leaf-level security Accounts */
    /* root account */
    private final Account root;
    /* Account Book */
    private final AccountBook accountBook;
    /* GainsCalc Type */
    private final GainsCalc gainsCalc;
    /* ReportConfig from panel or from test code */
    private static ReportConfig reportConfig;
    /* HashSet of InvestmentAccount Wrappers */
    private final HashSet<InvestmentAccountWrapper> investmentWrappers;

    public BulkSecInfo(AccountBook accountBook, ReportConfig reportConfig) throws Exception {
        this.accountBook = accountBook;
        this.root = accountBook.getRootAccount();
        BulkSecInfo.reportConfig = reportConfig;
        this.gainsCalc = reportConfig.useAverageCostBasis() ? new GainsAverageCalc() : new GainsLotMatchCalc();
        transactionSet = accountBook.getTransactionSet();
        firstDateInt = getFirstDateInt(transactionSet);
        LogController.logMessage(Level.FINE, String.format("First Date Defined for Transaction Set %s", firstDateInt));
        cashCurrencyWrapper = defineCashCurrency();
        LogController.logMessage(Level.FINE, "Loading all currencies of SECURITY type...");
        currencyWrappers = getCurrencyWrappersFromRoot();
        LogController.logMessage(Level.FINE, "SECURITY type currencies loaded, loading investment accounts...");
        investmentWrappers = getInvestmentAccountInfo(reportConfig);
        LogController.logMessage(Level.FINE, "All investment accounts, security acounts, and transactions loaded");
    }

    public final int getFirstDateInt(TransactionSet transactionSet){
        int dateInt = Integer.MAX_VALUE;
        for(AbstractTxn txn : transactionSet){
            dateInt = Math.min(txn.getDateInt(), dateInt);
        }
        return dateInt;
    }

    public static ReportConfig getReportConfig() { return BulkSecInfo.reportConfig;}

    /**
     * loads selected accounts into HashSet
     *
     * @param parentAcct parentTxn account for query (i.e. "retrieve all below")
     * @param acctTypes  integer designation of account types (varArg)
     * @return HashSet of Accounts
     */

    public static TreeSet<Account> getSelectedSubAccounts(Account parentAcct,
                                                          Account.AccountType... acctTypes) {
        int sz = parentAcct.getSubAccountCount();
        ArrayList<Account.AccountType> acctTypesList = new ArrayList<>();
        TreeSet<Account> SubAccts = new TreeSet<>(acctComp);
        if (acctTypes.length > 0) {
            Collections.addAll(acctTypesList, acctTypes);
        }
        for (int i = 0; i < sz; i++) {
            Account acct = parentAcct.getSubAccount(i);
            if (acctTypesList.contains(acct.getAccountType())
                    || acctTypes.length == 0) {
                SubAccts.add(acct);
            } // recursively add accounts
            SubAccts.addAll(getSelectedSubAccounts(acct, acctTypes));
        }
        return SubAccts; // note: includes accounts with no transactions!
    }

    /**
     * returns integer for customer sort order based on transaction type
     * (ensures buys come before sells on day trades, for example)
     *
     * @return Integer which represents sort order
     */
    public static Integer getTxnSortOrder(InvestTxnType transType) {
        return switch (transType) {
            case BUY_XFER -> 1;
            case DIVIDEND_REINVEST -> 2;
            case SELL -> 3;
            case SELL_XFER -> 4;
            case SHORT -> 5;
            case COVER -> 6;
            case MISCINC -> 7;
            case MISCEXP -> 8;
            case DIVIDEND -> 9;
            case DIVIDENDXFR -> 10;
            case BANK -> 11;
            default -> 0;
        };
    }

    /**
     * loads currency data into ArrayList of String Arrays
     *
     * @return ArrayList of String Arrays
     */
    public ArrayList<String[]> ListAllCurrenciesInfo() {
        ArrayList<String[]> currInfo = new ArrayList<>();

        for (CurrencyWrapper curWrapper : currencyWrappers.values()) {
            List<CurrencySnapshot> snapshots = curWrapper.currencyType.getSnapshots();
            currInfo.addAll(snapshots.stream().map(snapshot ->
                    loadCurrencySnapshotArray(curWrapper.currencyType, snapshot))
                    .collect(Collectors.toList()));
        }
        for (CurrencyType curType : this.getFXCurrencyTypesFromRoot().values()) {
            List<CurrencySnapshot> snapshots = curType.getSnapshots();
            currInfo.addAll(snapshots.stream().map(snapshot ->
                    loadCurrencySnapshotArray(curType, snapshot))
                    .collect(Collectors.toList()));
        }

        return currInfo;
    }

    public ArrayList<HashMap<String, String>> ListLastCurrencyUpdates() {
        HashMap<String, CurrencyWrapper> currencyWrappers = this.getCurrencyWrappers();
        ArrayList<HashMap<String, String>> outputList = new ArrayList<>();
        int dateInt = DateUtils.convertToDateInt(new Date());
        for (HashMap.Entry<String, CurrencyWrapper> entry : currencyWrappers.entrySet()) {
            CurrencySnapshot snapshot = entry.getValue().getSnapshotForDate(dateInt);
            if (snapshot != null) {
                outputList.add(new HashMap<>() {{
                    put("ticker", entry.getValue().getName());
                    put("long_name", entry.getValue().getCurrencyType().getName());
                    put("id", entry.getKey());
                    put("date", DateUtils.convertToStandard(snapshot.getDateInt()));
                    put("price", Double.toString(1.0 / snapshot.getRate()));
                }});
            }
        }
        return outputList;
    }

    /**
     * loads data from individual currency snapshot
     *
     * @param cur CurrencyType
     * @param snapshot   index of currency snapshot
     * @return String array of currency and price info
     */
    public static String[] loadCurrencySnapshotArray(CurrencyType cur, CurrencySnapshot snapshot) {
        ArrayList<String> currencyInfo = new ArrayList<>();
        currencyInfo.add(cur.getParameter("id"));
        currencyInfo.add(cur.getName());
        if (cur.getTickerSymbol().isEmpty()) {
            currencyInfo.add("NoTicker");
        } else {
            currencyInfo.add(cur.getTickerSymbol());
        }
        int todayDate = DateUtils.getLastCurrentDateInt();
        int dateInt = snapshot.getDateInt();
        double closeRate = snapshot.getRate();
        currencyInfo.add(DateUtils.convertToShort(dateInt));
        currencyInfo.add(Double.toString(1 / closeRate));
        currencyInfo.add(Double.toString(1 / cur.adjustRateForSplitsInt(dateInt,
                closeRate, todayDate)));
        return currencyInfo.toArray(new String[0]);
    }

    public static StringBuffer listCurrencySnapshotHeader() {
        StringBuffer currInfo = new StringBuffer();
        currInfo.append("id " + ",");
        currInfo.append("Name " + ",");
        currInfo.append("Ticker " + ",");
        currInfo.append("Date " + ",");
        currInfo.append("PricebyDate " + ",");
        currInfo.append("PriceByDate(Adjust)");
        return currInfo;
    }

    /**
     * Generates account associated with transaction (i.e. if a security is in
     * the transaction hierarchy, return that account, otherwise, return the
     * transaction account
     *
     * @param thisTxn transaction of interest
     * @return account associated with this transaction
     */
    public static Account getAssociatedAccount(AbstractTxn thisTxn) {
        Account associatedAccount = thisTxn.getAccount();
        ParentTxn parent;
        if (thisTxn instanceof ParentTxn) {
            parent = (ParentTxn) thisTxn;

        } else { // this is a split transaction
            SplitTxn split = (SplitTxn) thisTxn;
            parent = split.getParentTxn();
        }
        for (int i = 0; i < parent.getSplitCount(); i++) {
            if (parent.getSplit(i).getAccount().getAccountType()
                    == Account.AccountType.SECURITY)
                associatedAccount = parent.getSplit(i).getAccount();
        }
        return associatedAccount;
    }

    public TransactionSet getTransactionSet() {
        return transactionSet;
    }

    public CurrencyWrapper getCashCurrencyWrapper() {
        return cashCurrencyWrapper;
    }

    public HashMap<String, CurrencyWrapper> getCurrencyWrappers() {
        return currencyWrappers;
    }

    public int getFirstDateInt() {
        return firstDateInt;
    }

    public Account getRoot() {
        return root;
    }

    public AccountBook getAccountBook() {return accountBook;}

    public GainsCalc getGainsCalc() {
        return gainsCalc;
    }

    public HashSet<InvestmentAccountWrapper> getInvestmentWrappers() {
        return investmentWrappers;
    }

    /**
     * lists all TransactionValues in InvestmentAccountWrappers
     *
     * @return ArrayList of String Arrays with values
     */
    public ArrayList<String[]> listAllTransValues() {
        ArrayList<String[]> transactionsInfo = new ArrayList<>();

        for (InvestmentAccountWrapper investmentAccountWrapper : investmentWrappers) {
            transactionsInfo.addAll(investmentAccountWrapper.listTransValuesInfo());
        }
        return transactionsInfo;
    }

    /**
     * Defines a 'cash' currency whose value is always 1
     *
     * @return synthetic cash security, value 1
     */
    private CurrencyWrapper defineCashCurrency() {
        CurrencyTable currencyTable = accountBook.getCurrencies();
        CurrencyType cashCurrencyType;
        boolean cashCurrencyAbsent = currencyTable.getCurrencyByTickerSymbol("CASH") == null &&
                currencyTable.getCurrencyByName("CASH") == null;
        if(cashCurrencyAbsent){
            LogController.logMessage(Level.FINE, "Cash currency absent, creating...");
            cashCurrencyType = new CurrencyType(currencyTable);
            cashCurrencyType.setCurrencyType(CurrencyType.Type.SECURITY);
            cashCurrencyType.setName("CASH");
            cashCurrencyType.setTickerSymbol("CASH");
            cashCurrencyType.setRate(1.0, this.accountBook.getCurrencies().getBaseType());
            cashCurrencyType.setDecimalPlaces(4);
            cashCurrencyType.setParameter("asof_dt", firstDateInt);
            cashCurrencyType.addSnapshotInt(firstDateInt, 1.0, this.accountBook.getCurrencies().getBaseType());
        } else {
            cashCurrencyType = currencyTable.getCurrencyByTickerSymbol("CASH");
            LogController.logMessage(Level.FINE, "Cash currency already present, do not create...");
        }
        // ensure user rate is 1.0
        if(cashCurrencyType.getRelativeRate() != 1.0) cashCurrencyType.setRelativeRate(1.0);
        CurrencyWrapper cashCurrencyWrapper = new CurrencyWrapper(cashCurrencyType);
        cashCurrencyWrapper.setCash();
        // if verbose, report...
        if (reportConfig.getVerbose()){
            LogController.logMessage(Level.FINE, String.format("Base Type is %s",
                    this.accountBook.getCurrencies().getBaseType().getName()));
            LogController.logMessage(Level.FINE, String.format("Cash Currency Name is %s",
                    cashCurrencyType.getName()));
            LogController.logMessage(Level.FINE, String.format("Cash Currency Relative Type is %s",
                    cashCurrencyType.getRelativeCurrency().getName()));
            LogController.logMessage(Level.FINE, String.format("Cash Currency Type is %s",
                    cashCurrencyType.getCurrencyType().toString()));
            LogController.logMessage(Level.FINE, String.format("Currency Asof Date is %s",
                    cashCurrencyType.getParameter("asof_dt")));
            LogController.logMessage(Level.FINE, String.format("Cash Currency Relative Rate is %s",
                    cashCurrencyType.getRelativeRate()));
        }
        return cashCurrencyWrapper;
    }

    /**
     * creates map of currency ids and associated currency wrappers
     *
     * @return map of currency ids to associated currency wrappers
     */
    private HashMap<String, CurrencyWrapper> getCurrencyWrappersFromRoot() {
        List<CurrencyType> currencies = accountBook.getCurrencies().getAllCurrencies();
        HashMap<String, CurrencyWrapper> wrapperHashMap = new HashMap<>();
        currencies.stream().filter(currency -> currency.getCurrencyType() ==
                CurrencyType.Type.SECURITY).forEach(currency -> {
            String thisID = currency.getParameter("id");
            LogController.logMessage(Level.FINE, String.format("load currency: %s | %s", currency.getName(), thisID));
            wrapperHashMap.put(thisID, new CurrencyWrapper(currency));
        });
        // make sure new Currency is added!
        wrapperHashMap.put(cashCurrencyWrapper.getCurID(),
                cashCurrencyWrapper);

        return wrapperHashMap;
    }

    private HashMap<String, CurrencyType> getFXCurrencyTypesFromRoot() {
        List<CurrencyType> currencies = accountBook.getCurrencies().getAllCurrencies();
        HashMap<String, CurrencyType> currencyHashMap = new HashMap<>();
        currencies.stream().filter(currency -> currency.getCurrencyType() ==
                CurrencyType.Type.CURRENCY).forEach(currency -> {
            String thisID = currency.getParameter("id");
            currencyHashMap.put(thisID, currency);
        });
        return currencyHashMap;
    }

    /**
     * @param reportConfig Report Config from display
     * @return Completed InvestmentAccountWrappers with interpreted transaction
     * information
     * @throws Exception general exception
     */
    private HashSet<InvestmentAccountWrapper> getInvestmentAccountInfo(ReportConfig reportConfig) throws Exception {
        TreeSet<Account> allSubAccounts = getSelectedSubAccounts(root, Account.AccountType.INVESTMENT);
        HashSet<String> excludedAccountIds = reportConfig.getExcludedAccountIds();
        TreeSet<Account> selectedSubAccounts = new TreeSet<>(acctComp);
        selectedSubAccounts.addAll(allSubAccounts.stream().filter(account ->
                !excludedAccountIds.contains(account.getUUID())).collect(Collectors.toList()));
        HashSet<InvestmentAccountWrapper> invAcctWrappers = new HashSet<>();
        for (Account selectedSubAccount : selectedSubAccounts) {
            LogController.logMessage(Level.FINE, String.format("load Investment Acct: %s | %s",
                    selectedSubAccount.getAccountName(), selectedSubAccount.getUUID()));
            //Load investment account into Wrapper Class
            InvestmentAccountWrapper invAcctWrapper =
                    new InvestmentAccountWrapper(selectedSubAccount, this);
            invAcctWrappers.add(invAcctWrapper);
        } // end Investment Accounts Loop
        return invAcctWrappers;
    }

}

