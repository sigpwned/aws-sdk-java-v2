/*
 * Copyright 2010-2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package software.amazon.awssdk.extensions.dynamodb.mappingclient.operations;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.amazon.awssdk.extensions.dynamodb.mappingclient.AttributeValues.stringValue;
import static software.amazon.awssdk.extensions.dynamodb.mappingclient.functionaltests.models.FakeItem.createUniqueFakeItem;
import static software.amazon.awssdk.extensions.dynamodb.mappingclient.functionaltests.models.FakeItemWithSort.createUniqueFakeItemWithSort;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import software.amazon.awssdk.extensions.dynamodb.mappingclient.Expression;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.Key;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.MappedDatabase;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.MappedTable;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.MapperExtension;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.TableMetadata;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.extensions.ReadModification;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.extensions.WriteModification;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.operations.BatchWriteItem.BatchWriteItemResults;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.functionaltests.models.FakeItem;
import software.amazon.awssdk.extensions.dynamodb.mappingclient.functionaltests.models.FakeItemWithSort;

@RunWith(MockitoJUnitRunner.class)
public class BatchWriteItemTest {
    private static final String TABLE_NAME = "table-name";
    private static final String TABLE_NAME_2 = "table-name-2";

    private static final List<FakeItem> FAKE_ITEMS =
        IntStream.range(0, 6).mapToObj($ -> createUniqueFakeItem()).collect(toList());
    private static final List<Map<String, AttributeValue>> FAKE_ITEM_MAPS = FAKE_ITEMS.stream().map(item ->
        FakeItem.getTableSchema().itemToMap(item, FakeItem.getTableMetadata().primaryKeys())).collect(toList());
    private static final List<FakeItemWithSort> FAKESORT_ITEMS =
        IntStream.range(0, 6).mapToObj($ -> createUniqueFakeItemWithSort()).collect(toList());
    private static final List<Map<String, AttributeValue>> FAKESORT_ITEM_MAPS = FAKESORT_ITEMS.stream().map(item ->
        FakeItemWithSort.getTableSchema().itemToMap(item, FakeItemWithSort.getTableMetadata().primaryKeys()))
                                                                                              .collect(toList());
    private static final List<Key> FAKE_ITEM_KEYS =
        FAKE_ITEMS.stream().map(fakeItem -> Key.of(stringValue(fakeItem.getId()))).collect(toList());
    private static final List<Key> FAKESORT_ITEM_KEYS =
        FAKESORT_ITEMS.stream().map(fakeItemWithSort -> Key.of(stringValue(fakeItemWithSort.getId()),
                                                               stringValue(fakeItemWithSort.getSort()))).collect(toList());

    @Mock
    private DynamoDbClient mockDynamoDbClient;

    @Mock
    private MapperExtension mockExtension;

    private MappedDatabase mappedDatabase;
    private MappedTable<FakeItem> fakeItemMappedTable;
    private MappedTable<FakeItem> fakeItemMappedTableWithExtension;
    private MappedTable<FakeItemWithSort> fakeItemWithSortMappedTable;
    private MappedTable<FakeItemWithSort> fakeItemWithSortMappedTableWithExtension;

    @Before
    public void setupMappedTables() {
        mappedDatabase = MappedDatabase.builder().dynamoDbClient(mockDynamoDbClient).build();
        fakeItemMappedTable = mappedDatabase.table(TABLE_NAME, FakeItem.getTableSchema());
        fakeItemWithSortMappedTable = mappedDatabase.table(TABLE_NAME_2, FakeItemWithSort.getTableSchema());
        MappedDatabase mappedDatabaseWithExtension =
            MappedDatabase.builder().dynamoDbClient(mockDynamoDbClient).extendWith(mockExtension).build();
        fakeItemMappedTableWithExtension = mappedDatabaseWithExtension.table(TABLE_NAME, FakeItem.getTableSchema());
        fakeItemWithSortMappedTableWithExtension = mappedDatabaseWithExtension.table(TABLE_NAME_2,
                                                                                     FakeItemWithSort.getTableSchema());
    }

    @Test
    public void getServiceCall_makesTheRightCallAndReturnsResponse() {
        BatchWriteItem operation =
            BatchWriteItem.of(WriteBatch.of(fakeItemMappedTable, PutItem.of(FAKE_ITEMS.get(0))));

        WriteRequest writeRequest =
            WriteRequest.builder()
                        .putRequest(PutRequest.builder().item(FAKE_ITEM_MAPS.get(0)).build())
                        .build();

        BatchWriteItemRequest request =
            BatchWriteItemRequest.builder()
                                 .requestItems(singletonMap("table", singletonList(writeRequest)))
                                 .build();

        BatchWriteItemResponse expectedResponse = BatchWriteItemResponse.builder().build();
        when(mockDynamoDbClient.batchWriteItem(any(BatchWriteItemRequest.class))).thenReturn(expectedResponse);

        BatchWriteItemResponse response = operation.serviceCall(mockDynamoDbClient).apply(request);

        assertThat(response, sameInstance(expectedResponse));
        verify(mockDynamoDbClient).batchWriteItem(request);
    }

    @Test
    public void generateRequest_multipleTables_mixedCommands() {
        BatchWriteItem operation =
            BatchWriteItem.of(WriteBatch.of(fakeItemMappedTable,
                                            PutItem.of(FAKE_ITEMS.get(0)),
                                            DeleteItem.of(FAKE_ITEM_KEYS.get(1)),
                                            PutItem.of(FAKE_ITEMS.get(2))),
                              WriteBatch.of(fakeItemWithSortMappedTable,
                                            DeleteItem.of(FAKESORT_ITEM_KEYS.get(0)),
                                            PutItem.of(FAKESORT_ITEMS.get(1)),
                                            DeleteItem.of(FAKESORT_ITEM_KEYS.get(2))));

        BatchWriteItemRequest request = operation.generateRequest(mockExtension);

        List<WriteRequest> writeRequests1 = request.requestItems().get(TABLE_NAME);
        List<WriteRequest> writeRequests2 = request.requestItems().get(TABLE_NAME_2);
        assertThat(writeRequests1, containsInAnyOrder(putRequest(FAKE_ITEM_MAPS.get(0)),
                                                      deleteRequest(FAKE_ITEM_MAPS.get(1)),
                                                      putRequest(FAKE_ITEM_MAPS.get(2))));
        assertThat(writeRequests2, containsInAnyOrder(deleteRequest(FAKESORT_ITEM_MAPS.get(0)),
                                                      putRequest(FAKESORT_ITEM_MAPS.get(1)),
                                                      deleteRequest(FAKESORT_ITEM_MAPS.get(2))));
    }

    @Test
    public void generateRequest_multipleTables_extensionOnlyTransformsPutsAndNotDeletes() {
        BatchWriteItem operation =
            BatchWriteItem.of(WriteBatch.of(fakeItemMappedTableWithExtension,
                                            PutItem.of(FAKE_ITEMS.get(0)),
                                            DeleteItem.of(FAKE_ITEM_KEYS.get(1)),
                                            PutItem.of(FAKE_ITEMS.get(2))),
                              WriteBatch.of(fakeItemWithSortMappedTableWithExtension,
                                            DeleteItem.of(FAKESORT_ITEM_KEYS.get(0)),
                                            PutItem.of(FAKESORT_ITEMS.get(1)),
                                            DeleteItem.of(FAKESORT_ITEM_KEYS.get(2))));

        // Use the mock extension to transform every item based on table name
        IntStream.range(0, 3).forEach(i -> {
            lenient().doReturn(WriteModification.builder().transformedItem(FAKE_ITEM_MAPS.get(i + 3)).build())
                .when(mockExtension)
                .beforeWrite(eq(FAKE_ITEM_MAPS.get(i)),
                             argThat(operationContext -> operationContext.tableName().equals(TABLE_NAME)),
                             any());
            lenient().doReturn(WriteModification.builder().transformedItem(FAKESORT_ITEM_MAPS.get(i + 3)).build())
                .when(mockExtension)
                .beforeWrite(eq(FAKESORT_ITEM_MAPS.get(i)),
                             argThat(operationContext -> operationContext.tableName().equals(TABLE_NAME_2)),
                             any());
        });

        BatchWriteItemRequest request = operation.generateRequest(mockExtension);

        List<WriteRequest> writeRequests1 = request.requestItems().get(TABLE_NAME);
        List<WriteRequest> writeRequests2 = request.requestItems().get(TABLE_NAME_2);

        // Only PutItem requests should have their attributes transformed
        assertThat(writeRequests1, containsInAnyOrder(putRequest(FAKE_ITEM_MAPS.get(3)),
                                                      deleteRequest(FAKE_ITEM_MAPS.get(1)),
                                                      putRequest(FAKE_ITEM_MAPS.get(5))));
        assertThat(writeRequests2, containsInAnyOrder(deleteRequest(FAKESORT_ITEM_MAPS.get(0)),
                                                      putRequest(FAKESORT_ITEM_MAPS.get(4)),
                                                      deleteRequest(FAKESORT_ITEM_MAPS.get(2))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void generateRequest_extensionTriesToAddConditionalToPutItem() {
        BatchWriteItem operation =
            BatchWriteItem.of(WriteBatch.of(fakeItemMappedTableWithExtension,
                                            PutItem.of(FAKE_ITEMS.get(0)),
                                            DeleteItem.of(FAKE_ITEM_KEYS.get(1)),
                                            PutItem.of(FAKE_ITEMS.get(2))),
                              WriteBatch.of(fakeItemWithSortMappedTableWithExtension,
                                            DeleteItem.of(FAKESORT_ITEM_KEYS.get(0)),
                                            PutItem.of(FAKESORT_ITEMS.get(1)),
                                            DeleteItem.of(FAKESORT_ITEM_KEYS.get(2))));

        Expression expression = Expression.builder().expression("test-expression").build();

        doReturn(WriteModification.builder().additionalConditionalExpression(expression).build())
            .when(mockExtension)
            .beforeWrite(anyMap(), any(), any(TableMetadata.class));

        operation.generateRequest(mockExtension);
    }

    @Test
    public void transformResults_multipleUnprocessedOperations() {
        BatchWriteItem operation = BatchWriteItem.of();

        List<WriteRequest> writeRequests1 = Arrays.asList(putRequest(FAKE_ITEM_MAPS.get(0)),
                                                          deleteRequest(FAKE_ITEM_MAPS.get(1)),
                                                          deleteRequest(FAKE_ITEM_MAPS.get(2)));
        List<WriteRequest> writeRequests2 = Arrays.asList(deleteRequest(FAKESORT_ITEM_MAPS.get(0)),
                                                          putRequest(FAKESORT_ITEM_MAPS.get(1)),
                                                          putRequest(FAKESORT_ITEM_MAPS.get(2)));
        Map<String, List<WriteRequest>> writeRequests = new HashMap<>();
        writeRequests.put(TABLE_NAME, writeRequests1);
        writeRequests.put(TABLE_NAME_2, writeRequests2);
        BatchWriteItemResponse response =
            BatchWriteItemResponse.builder()
                                  .unprocessedItems(writeRequests)
                                  .build();

        BatchWriteItemResults results = operation.transformResponse(response, mockExtension);

        assertThat(results.unprocessedDeleteItemsForTable(fakeItemMappedTableWithExtension),
                   containsInAnyOrder(FAKE_ITEMS.get(1), FAKE_ITEMS.get(2)));
        assertThat(results.unprocessedPutItemsForTable(fakeItemMappedTableWithExtension),
                   containsInAnyOrder(FAKE_ITEMS.get(0)));
        assertThat(results.unprocessedDeleteItemsForTable(fakeItemWithSortMappedTableWithExtension),
                   containsInAnyOrder(FAKESORT_ITEMS.get(0)));
        assertThat(results.unprocessedPutItemsForTable(fakeItemWithSortMappedTableWithExtension),
                   containsInAnyOrder(FAKESORT_ITEMS.get(1), FAKESORT_ITEMS.get(2)));
    }

    @Test
    public void transformResults_multipleUnprocessedOperations_extensionTransformsPutsNotDeletes() {
        BatchWriteItem operation = BatchWriteItem.of();

        List<WriteRequest> writeRequests1 = Arrays.asList(putRequest(FAKE_ITEM_MAPS.get(0)),
                                                          deleteRequest(FAKE_ITEM_MAPS.get(1)),
                                                          deleteRequest(FAKE_ITEM_MAPS.get(2)));
        List<WriteRequest> writeRequests2 = Arrays.asList(deleteRequest(FAKESORT_ITEM_MAPS.get(0)),
                                                          putRequest(FAKESORT_ITEM_MAPS.get(1)),
                                                          putRequest(FAKESORT_ITEM_MAPS.get(2)));
        Map<String, List<WriteRequest>> writeRequests = new HashMap<>();
        writeRequests.put(TABLE_NAME, writeRequests1);
        writeRequests.put(TABLE_NAME_2, writeRequests2);
        BatchWriteItemResponse response =
            BatchWriteItemResponse.builder()
                                  .unprocessedItems(writeRequests)
                                  .build();

        // Use the mock extension to transform every item based on table name
        IntStream.range(0, 3).forEach(i -> {
            lenient().doReturn(ReadModification.builder().transformedItem(FAKE_ITEM_MAPS.get(i + 3)).build())
                     .when(mockExtension)
                     .afterRead(eq(FAKE_ITEM_MAPS.get(i)),
                                argThat(operationContext -> operationContext.tableName().equals(TABLE_NAME)),
                                any());
            lenient().doReturn(ReadModification.builder().transformedItem(FAKESORT_ITEM_MAPS.get(i + 3)).build())
                     .when(mockExtension)
                     .afterRead(eq(FAKESORT_ITEM_MAPS.get(i)),
                                argThat(operationContext -> operationContext.tableName().equals(TABLE_NAME_2)),
                                any());
        });

        BatchWriteItemResults results = operation.transformResponse(response, mockExtension);

        assertThat(results.unprocessedDeleteItemsForTable(fakeItemMappedTableWithExtension),
                   containsInAnyOrder(FAKE_ITEMS.get(1), FAKE_ITEMS.get(2)));
        assertThat(results.unprocessedPutItemsForTable(fakeItemMappedTableWithExtension),
                   containsInAnyOrder(FAKE_ITEMS.get(3)));
        assertThat(results.unprocessedDeleteItemsForTable(fakeItemWithSortMappedTableWithExtension),
                   containsInAnyOrder(FAKESORT_ITEMS.get(0)));
        assertThat(results.unprocessedPutItemsForTable(fakeItemWithSortMappedTableWithExtension),
                   containsInAnyOrder(FAKESORT_ITEMS.get(4), FAKESORT_ITEMS.get(5)));
    }

    @Test
    public void transformResults_noUnprocessedOperations() {
        BatchWriteItem operation = BatchWriteItem.of();

        BatchWriteItemResponse response =
            BatchWriteItemResponse.builder()
                                  .unprocessedItems(emptyMap())
                                  .build();

        BatchWriteItemResults results = operation.transformResponse(response, mockExtension);

        assertThat(results.unprocessedDeleteItemsForTable(fakeItemMappedTable), is(emptyList()));
        assertThat(results.unprocessedPutItemsForTable(fakeItemMappedTable), is(emptyList()));
    }

    private static WriteRequest putRequest(Map<String, AttributeValue> itemMap) {
        return WriteRequest.builder().putRequest(PutRequest.builder().item(itemMap).build()).build();
    }

    private static WriteRequest deleteRequest(Map<String, AttributeValue> itemMap) {
        return WriteRequest.builder().deleteRequest(DeleteRequest.builder().key(itemMap).build()).build();
    }
}