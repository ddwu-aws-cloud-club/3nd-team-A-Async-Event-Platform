package com.teamA.async.ingest.ddb;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.common.domain.model.UserItem;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
@RequiredArgsConstructor
public class UserRepository {
    private final DynamoDbEnhancedClient enhancedClient;

    @Value("${aws.dynamodb.table-name}")
    private String tableName;

    private DynamoDbTable<UserItem> getTable() {
        return enhancedClient.table(tableName, TableSchema.fromBean(UserItem.class));
    }

    public void save(UserItem user) {
        user.generateKeys(); // PK="USER#dyjung", SK="ACCOUNT"
        getTable().putItem(user);
    }

    public UserItem findByUserId(String userId) {
        // 정확히 유저 아이템만 가져오기 위해 PK와 SK를 모두 지정
        Key key = Key.builder()
                .partitionValue(DdbKeyFactory.userPk(userId))
                .sortValue(DdbKeyFactory.accountSk())
                .build();
        return getTable().getItem(r -> r.key(key));
    }
}