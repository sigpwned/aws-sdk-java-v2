/*
 * Copyright 2010-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.enhanced.dynamodb.converter.attribute.bundled;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.OptionalLong;
import software.amazon.awssdk.annotations.Immutable;
import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.annotations.ThreadSafe;
import software.amazon.awssdk.enhanced.dynamodb.converter.attribute.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.converter.attribute.ConversionContext;
import software.amazon.awssdk.enhanced.dynamodb.converter.string.bundled.OptionalLongStringConverter;
import software.amazon.awssdk.enhanced.dynamodb.model.ItemAttributeValue;
import software.amazon.awssdk.enhanced.dynamodb.model.TypeConvertingVisitor;
import software.amazon.awssdk.enhanced.dynamodb.model.TypeToken;

/**
 * A converter between {@link OptionalLong} and {@link ItemAttributeValue}.
 *
 * <p>
 * This stores values in DynamoDB as a number.
 *
 * <p>
 * This supports reading numbers between {@link Long#MIN_VALUE} and {@link Long#MAX_VALUE} from DynamoDB. Null values are
 * converted to {@code OptionalLong.empty()}. For larger numbers, consider using the {@link OptionalSubtypeAttributeConverter}
 * along with a {@link BigInteger}. For shorter numbers, consider using the {@link OptionalIntAttributeConverter} or
 * {@link OptionalSubtypeAttributeConverter} along with a {@link Short} type.
 *
 * <p>
 * This does not support reading decimal numbers. For decimal numbers, consider using {@link OptionalDoubleAttributeConverter},
 * or the {@link OptionalSubtypeAttributeConverter} with a {@link Float} or {@link BigDecimal}. Decimal numbers will cause a
 * {@link NumberFormatException} on conversion.
 *
 * <p>
 * This can be created via {@link #create()}.
 */
@SdkPublicApi
@ThreadSafe
@Immutable
public final class OptionalLongAttributeConverter implements AttributeConverter<OptionalLong> {
    private static final Visitor VISITOR = new Visitor();
    private static final OptionalLongStringConverter STRING_CONVERTER = OptionalLongStringConverter.create();

    private OptionalLongAttributeConverter() {}

    @Override
    public TypeToken<OptionalLong> type() {
        return TypeToken.of(OptionalLong.class);
    }

    public static OptionalLongAttributeConverter create() {
        return new OptionalLongAttributeConverter();
    }

    @Override
    public ItemAttributeValue toAttributeValue(OptionalLong input, ConversionContext context) {
        if (input.isPresent()) {
            return ItemAttributeValue.fromNumber(STRING_CONVERTER.toString(input));
        } else {
            return ItemAttributeValue.nullValue();
        }
    }

    @Override
    public OptionalLong fromAttributeValue(ItemAttributeValue input,
                                           ConversionContext context) {
        return input.convert(VISITOR);
    }

    private static final class Visitor extends TypeConvertingVisitor<OptionalLong> {
        private Visitor() {
            super(OptionalLong.class, OptionalLongAttributeConverter.class);
        }

        @Override
        public OptionalLong convertNull() {
            return OptionalLong.empty();
        }

        @Override
        public OptionalLong convertString(String value) {
            return STRING_CONVERTER.fromString(value);
        }

        @Override
        public OptionalLong convertNumber(String value) {
            return STRING_CONVERTER.fromString(value);
        }
    }
}