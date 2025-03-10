/*
 * DateRange.java
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

import java.util.Date;

/**
 * Full Date information for a report, to include
 * specific dates and an optional date rule
 */
public final class DateRange {
    private static final int nullDateInt = 19000101;
    private int fromDateInt;
    private int toDateInt;
    private int snapDateInt;
    private final REF_DATE refDate;
    private final DATE_RULE dateRule;
    private final Boolean snapDateIsRefDate;

    public DateRange() {
        this.refDate = REF_DATE.NONE;
        this.dateRule = DATE_RULE.NONE;
        this.snapDateIsRefDate = null;
        this.setFromDateInt(nullDateInt);
        this.setToDateInt();
        this.setSnapDateInt();

    }

    public DateRange(int fromDateInt, int toDateInt, int snapDateInt) {
        this.fromDateInt = fromDateInt;
        this.toDateInt = toDateInt;
        this.snapDateInt = snapDateInt;
        this.refDate = REF_DATE.NONE;
        this.dateRule = DATE_RULE.NONE;
        snapDateIsRefDate = null;
    }

    public DateRange(REF_DATE refDate, DATE_RULE dateRule, boolean snapDateIsRefDate) {
        this.refDate = refDate;
        this.dateRule = dateRule;
        int refDateInt = DateUtils.convertToDateInt(new Date());
        if (refDate == REF_DATE.LAST_TRADE_DATE) refDateInt = DateUtils.getLastCurrentDateInt();
        this.snapDateIsRefDate = snapDateIsRefDate;
        int startDateInt = switch (dateRule) {
            case ONE_DAY -> DateUtils.getPrevBusinessDay(refDateInt);
            case ONE_WEEK -> DateUtils.getLatestBusinessDay(DateUtils.addDaysInt(refDateInt, -7));
            case ONE_MONTH -> DateUtils.getLatestBusinessDay(DateUtils.addMonthsInt(refDateInt, -1));
            case ONE_YEAR -> DateUtils.getLatestBusinessDay(DateUtils.addMonthsInt(refDateInt, -12));
            case MONTH_TO_DATE -> DateUtils.getStartMonth(refDateInt);
            case QUARTER_TO_DATE -> DateUtils.getStartQuarter(refDateInt);
            case YEAR_TO_DATE -> DateUtils.getStartYear(refDateInt);
            case THREE_YEARS -> DateUtils.getLatestBusinessDay(DateUtils.addMonthsInt(refDateInt, -36));
            case FIVE_YEARS -> DateUtils.getLatestBusinessDay(DateUtils.addMonthsInt(refDateInt, -60));
            case TEN_YEARS -> DateUtils.getLatestBusinessDay(DateUtils.addMonthsInt(refDateInt, -120));
            default -> refDateInt;
        };
        this.fromDateInt = startDateInt;
        this.toDateInt = refDateInt;
        this.snapDateInt = snapDateIsRefDate ? refDateInt : startDateInt;
    }

    @SuppressWarnings("unused")
    public static String[] getRefDateOptions() {
        String[] output = new String[REF_DATE.values().length];
        int i = 0;
        for (REF_DATE refDate : REF_DATE.values()) {
            output[i] = refDate.name();
            i++;
        }
        return output;
    }

    @SuppressWarnings("unused")
    public static String[] getDateRuleOptions() {
        String[] output = new String[DATE_RULE.values().length];
        int i = 0;
        for (DATE_RULE dateRule : DATE_RULE.values()) {
            output[i] = dateRule.name();
            i++;
        }
        return output;
    }

    public static DateRange getDefaultDateRange() {
        return new DateRange(REF_DATE.LAST_TRADE_DATE, DATE_RULE.YEAR_TO_DATE, true);
    }

    /**
     * @param prefString String from preferences
     * @return DateRange stored in preferences
     */
    public static DateRange getDateRangeFromString(String prefString) {
            String[] prefElements = prefString.split(",");
            if (!prefElements[3].equals(REF_DATE.NONE.name()) && !prefElements[4].equals(DATE_RULE.NONE.name())) { //DateRule
                return new DateRange(REF_DATE.valueOf(prefElements[3]),
                        DATE_RULE.valueOf(prefElements[4]), prefElements[5].equals("true"));
            } else {
                int fromDateInt = Integer.parseInt(prefElements[0]);
                int toDateInt = Integer.parseInt(prefElements[1]);
                int snapDateInt = Integer.parseInt(prefElements[2]);
                return new DateRange(fromDateInt, toDateInt, snapDateInt);
            }
    }

    /**
     * Determines if Date Range is valid (i.e. has either (1) a valid date rule
     * (2) a valid from/to date and a valid snapshot date
     *
     * @return true if valid Date Range
     */
    public boolean isValid() {
        boolean isValidDateRange = false;
        if(refDate != REF_DATE.NONE && dateRule != DATE_RULE.NONE){
            isValidDateRange = true;
        } else if(fromDateInt != nullDateInt && toDateInt != nullDateInt && snapDateInt != nullDateInt) {
            Date fromDateLong = DateUtils.convertToDate(fromDateInt);
            Date toDateLong = DateUtils.convertToDate(toDateInt);
            isValidDateRange = toDateLong.after(fromDateLong);
        }
        return isValidDateRange;
    }

    public static int getNullDateInt(){
        return nullDateInt;
    }

    public int getSnapDateInt() {
        return snapDateInt;
    }

    void setSnapDateInt() {
        this.snapDateInt = DateRange.nullDateInt;
    }

    public int getFromDateInt() {
        return fromDateInt;
    }

    void setFromDateInt(int fromDateInt) {
        this.fromDateInt = fromDateInt;
    }

    public int getToDateInt() {
        return toDateInt;
    }

    void setToDateInt() {
        this.toDateInt = DateRange.nullDateInt;
    }

    public REF_DATE getRefDate() {
        return refDate;
    }

    public DATE_RULE getDateRule() {
        return dateRule;
    }

    @Override
    public String toString() {
        return fromDateInt + "," + toDateInt + "," + snapDateInt + "," +
                refDate + "," + dateRule + "," + snapDateIsRefDate;
    }

    public enum REF_DATE {NONE, TODAY, LAST_TRADE_DATE}

    public enum DATE_RULE {
        NONE, ONE_DAY, ONE_WEEK, ONE_MONTH, ONE_YEAR, MONTH_TO_DATE,
        QUARTER_TO_DATE, YEAR_TO_DATE, THREE_YEARS, FIVE_YEARS, TEN_YEARS
    }

}