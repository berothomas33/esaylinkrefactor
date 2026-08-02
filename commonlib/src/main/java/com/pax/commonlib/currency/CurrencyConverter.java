/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2019-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                  Author	                 Action
 * 20190108  	         Kim.L                   Create
 * ===========================================================================================
 */
package com.pax.commonlib.currency;

import android.os.Build;
import androidx.annotation.NonNull;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public class CurrencyConverter {

    private static final String TAG = "CurrencyConv";
    private static final List<Locale> locales = new ArrayList<>();

    private static Locale defLocale = Locale.US;

    private static boolean isUsingDefaultCurrencyStrategy = true;

    static {
        Locale[] tempLocales = Locale.getAvailableLocales();
        for (Locale i : tempLocales) {
            try {
                CountryCode country = CountryCode.getByCode(i.getISO3Country());
                Currency.getInstance(i); // just for filtering
                if (country != null) {
                    locales.add(i);
                }
            } catch (Exception ignored) {
                // ignore, because print log may cost too much time
            }
        }
    }

    private CurrencyConverter() {
        //do nothing
    }

    public static List<Locale> getSupportedLocale() {
        return locales;
    }

    public static void getSupportedLocaleList(List<String> contentList) {
        List<Locale> locales = CurrencyConverter.getSupportedLocale();
        for (Locale locale : locales) {
            contentList.add(locale.getDisplayName(Locale.US));
        }
        Collections.sort(contentList);
    }

    /**
     * @param countryName : {@see Locale#getDisplayName(Locale)}
     */
    public static Locale setDefCurrency(String countryName) {
        for (Locale i : locales) {
            if (i.getDisplayName(Locale.US).equals(countryName)) {
                if (!i.equals(defLocale)) {
                    defLocale = i;
                    Locale.setDefault(defLocale);
                }
                return defLocale;
            }
        }
        return defLocale;
    }

    public static Locale getDefCurrency() {
        return defLocale;
    }

    private static String modifyCurrencyFormat(String currencyCode, String result) {
//        if (currencyCode.equals("IDR")) {
//            result = result.replace(".",",");
//        }
        return result;
    }

    private static String recoverCurrencyFormat(String currencyCode, String result) {
//        if (currencyCode.equals("IDR")) {
//            return  result.replace(",", ".");
//        }
        return result;
    }

    public static int getDigitsNum(Currency currency) {
        int digits = currency.getDefaultFractionDigits();
//        switch (currency.getCurrencyCode()) {
//            case "XAF":
//            case "LBP":
//            case "TZS":
//            case "XOF":
//            case "MUR":
//            case "PKR":
//            case "RWF":
//            case "COP":
//            case "CRC":
//            case "PYG":
//            case "BIF":
//            case "ISK":
//            case "MNT":
//            case "ALL":
//            case "RSD":
//            case "UZS":
//            case "TND":
//                digits = 2;
//                break;
//            default:
//                break;
//        }
        return digits;
    }

    public static int getDigitsNum() {
        Currency currency = Currency.getInstance(defLocale);
        return getDigitsNum(currency);
    }

    public static NumberFormat getFormatter(Locale locale, Currency currency, int digits, boolean isCurrency) {
        NumberFormat formatter;
        if (isCurrency) {
            formatter = NumberFormat.getCurrencyInstance(locale);
        } else {
            formatter = NumberFormat.getNumberInstance(locale);
        }
        formatter.setMinimumFractionDigits(digits);
        formatter.setMaximumFractionDigits(digits);
        return formatter;
    }

    public static boolean isIsUsingDefaultCurrencyStrategy() {
        return isUsingDefaultCurrencyStrategy;
    }

    public static void setIsUsingDefaultCurrencyStrategy(boolean isUsingDefaultCurrencyStrategy) {
        CurrencyConverter.isUsingDefaultCurrencyStrategy = isUsingDefaultCurrencyStrategy;
    }

    /**
     * @param amount
     * @return
     */
    public static String convert(long amount) {
        return convert(amount, defLocale);
    }

    /**
     * @param amount
     * @param locale
     * @return
     */
    public static String convert(long amount, Locale locale) {
        return convert(amount, defLocale, isUsingDefaultCurrencyStrategy);
    }

    public static String convert(long amount, Locale locale, boolean isCurrency) {
        String result;
        Currency currency = Currency.getInstance(locale);
        if (currency != null) {
            int digits = getDigitsNum(currency);
            long newAmount = amount < 0 ? -amount : amount; // AET-58
            String prefix = amount < 0 ? "-" : "";
            try {
                double amt = (double) newAmount / (Math.pow(10, digits));
                String currencyCode = currency.getCurrencyCode();
                NumberFormat formatter = getFormatter(locale, currency, digits, isCurrency);
                if (isCurrency) {
                    result = prefix + formatter.format(amt);
                } else {
                    currencyCode = currencyCode.equalsIgnoreCase("CNY") ? "RMB" : currencyCode;
                    result = prefix + currencyCode + formatter.format(amt);
                }
                return modifyCurrencyFormat(currencyCode, result);
            } catch (IllegalArgumentException e) {
                LogUtils.e(TAG, "", e);
            }
        }
        return "";
    }

    public static Long parse(String formatterAmount) {
        return parse(formatterAmount, defLocale);
    }

    public static Long parse(String formatterAmount, Locale locale) {
        return parse(formatterAmount, locale, isUsingDefaultCurrencyStrategy);
    }

    public static Long parse(String formatterAmount, Locale locale, boolean isCurrency) {
        String amount;
        Currency currency = Currency.getInstance(locale);
        int digits = getDigitsNum(currency);
        String newFormatterAmount = formatterAmount;

        String currencyCode = currency.getCurrencyCode();
        if (isCurrency) {
            amount = newFormatterAmount;
        } else {
            currencyCode = currency.getCurrencyCode().equals("CNY") ? "RMB" : currency.getCurrencyCode();
            amount = dealSignSymbol(formatterAmount, newFormatterAmount, currencyCode);
        }

        amount = recoverCurrencyFormat(currencyCode, amount);

        NumberFormat formatter = getFormatter(locale, currency, digits, isCurrency);
        try {
            Number num = formatter.parse(amount);

            return Math.round(num.doubleValue() * Math.pow(10, digits));
        } catch (ParseException | NumberFormatException e) {
            LogUtils.e(TAG, "", e);
        }
        return 0L;
    }

    public static Long parseParam(String formatterAmount) {
        Currency currency = Currency.getInstance(defLocale);
        int digits = getDigitsNum(currency);
        try {
            double num = Double.parseDouble(formatterAmount);
            return Math.round(num * Math.pow(10, digits));
        } catch (NumberFormatException e) {
            LogUtils.e(TAG, "", e);
        }
        return 0L;
    }

    @NonNull
    private static String dealSignSymbol(String formatterAmount, String newFormatterAmount, String currencyCode) {
        String amount;
        if ("-".equals(formatterAmount.substring(0, 1))) {
            newFormatterAmount = formatterAmount.substring(1);
        }
        if (currencyCode.equals(newFormatterAmount.substring(0, currencyCode.length()))) {
            amount = newFormatterAmount.substring(currencyCode.length());
        } else {
            amount = newFormatterAmount;
        }
        return amount;
    }

    public static byte[] getCurrencyCode() {
        Currency currency = Currency.getInstance(defLocale);
        String currencyCode = currency.getCurrencyCode();
        LogUtils.i(TAG, "currency symbol:" + currency.getSymbol() + ", Currency code:" + currencyCode + ",fraction:" + currency.getDefaultFractionDigits());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LogUtils.d(TAG, "numeric code:" + currency.getNumericCode());
            return ConvertUtils.strToBcdPaddingLeft(String.valueOf(currency.getNumericCode()));
        }
        return new byte[]{0x08, 0x40};
    }

}
