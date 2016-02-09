package com.google.zxing.demo.module;

import android.text.TextUtils;

public enum QRCodeTypeDefine
{
	None("none"), isMIC("1");

	private String value;

	private QRCodeTypeDefine(String value)
	{
		this.value = value;
	}

	public static QRCodeTypeDefine getValueByTag(String type)
	{
		if (TextUtils.isEmpty(type))
		{
			return None;
		}
		for (QRCodeTypeDefine target : QRCodeTypeDefine.values())
		{
			if (target.value.equals(type))
			{
				return target;
			}
		}
		return None;
	}

	public static String getValue(QRCodeTypeDefine target)
	{
		return target.value;
	}
}
