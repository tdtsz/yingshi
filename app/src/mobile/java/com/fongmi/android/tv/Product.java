package com.fongmi.android.tv;

import android.content.Context;

import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.ResUtil;

public class Product {

    public static int getDeviceType() {
        return 1;
    }

    public static int getColumn() {
        return Math.abs(Setting.getSize() - 5);
    }

    public static int getColumn(Vod.Style style) {
        return style.isLand() ? getColumn() - 1 : getColumn();
    }

    public static int[] getSpec(Context context) {
        return getSpec(context, Vod.Style.rect());
    }

    public static int[] getSpec(Context context, Vod.Style style) {
        int column = getColumn(style);
        int space = ResUtil.dp2px(32) + ResUtil.dp2px(16 * (column - 1));
        if (style.isOval()) space += ResUtil.dp2px(column * 16);
        return getSpec(context, space, column, style);
    }

    public static int[] getSpec(Context context, int space, int column) {
        return getSpec(context, space, column, Vod.Style.rect());
    }

    private static int[] getSpec(Context context, int space, int column, Vod.Style style) {
        int base = ResUtil.getScreenWidth(context) - space;
        int width = base / column;
        int height = (int) (width / style.getRatio());
        return new int[]{width, height};
    }

    public static int getEms() {
        return Math.min(ResUtil.getScreenWidth() / ResUtil.sp2px(18), 35);
    }
}
