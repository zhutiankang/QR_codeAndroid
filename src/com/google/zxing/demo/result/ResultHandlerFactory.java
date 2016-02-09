/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.demo.result;

import com.google.zxing.core.Result;
import com.google.zxing.core.client.result.ParsedResult;
import com.google.zxing.core.client.result.ParsedResultType;
import com.google.zxing.core.client.result.ResultParser;

/**
 * Manufactures Android-specific handlers based on the barcode content's type.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ResultHandlerFactory
{
	private ResultHandlerFactory()
	{
	}

	/**
	 * 
	 * @param rawResult uri:URI text:Text
	 * @return
	 */
	public static String makeResultHandler(Result rawResult)
	{
		ParsedResult result = parseResult(rawResult);
		switch (result.getType())
		{
		case URI:
			return ParsedResultType.URI.toString();
		default:
			return ParsedResultType.TEXT.toString();
		}
	}

	private static ParsedResult parseResult(Result rawResult)
	{
		return ResultParser.parseResult(rawResult);
	}
}
