/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.async.client

import com.mongodb.MongoNamespace
import com.mongodb.async.FutureResultCallback
import org.bson.Document
import spock.lang.IgnoreIf

import static com.mongodb.ClusterFixture.serverVersionAtLeast
import static com.mongodb.async.client.Fixture.getMongoClient
import static java.util.Arrays.asList
import static java.util.concurrent.TimeUnit.SECONDS

class SmokeTestSpecification extends FunctionalSpecification {

    def 'should handle common CRUD scenarios without error'() {
        given:
        def mongoClient = getMongoClient()
        def database = mongoClient.getDatabase(databaseName)
        def collection = database.getCollection(collectionName)

        when:
        def document = new Document('_id', 1)
        def updatedDocument = new Document('_id', 1).append('a', 1)

        then: 'The count is zero'
        run(collection.&count) == 0

        then: 'find first should return null if no documents'
        run(collection.find().&first) == null

        then: 'Insert a document'
        run(collection.&insertOne, document) == null

        then: 'The count is one'
        run(collection.&count) == 1

        then: 'find that document'
        run(collection.find().&first) == document

        then: 'update that document'
        run(collection.&updateOne, document, new Document('$set', new Document('a', 1))).wasAcknowledged()

        then: 'find the updated document'
        run(collection.find().&first) == updatedDocument

        then: 'aggregate the collection'
        run(collection.aggregate([new Document('$match', new Document('a', 1))]).&first) == updatedDocument

        then: 'remove all documents'
        run(collection.&deleteOne, new Document()).getDeletedCount() == 1

        then: 'The count is zero'
        run(collection.&count) == 0
    }

    @IgnoreIf({ !serverVersionAtLeast(asList(2, 6, 0)) })
    def 'should aggregate to collection'() {
        given:
        def mongoClient = getMongoClient()
        def database = mongoClient.getDatabase(databaseName)
        def collection = database.getCollection(collectionName)

        when:
        def document = new Document('_id', 1).append('a', 1)
        run(collection.&insertOne, document)

        then: 'aggregate the collection to a collection'
        run(collection.aggregate([new Document('$match', new Document('a', 1)), new Document('$out', getClass().getName() + '.out')])
                      .&first) == document
    }

    def 'should handle common administrative scenarios without error'() {
        given:
        def mongoClient = getMongoClient()
        def database = mongoClient.getDatabase(databaseName)
        def collection = database.getCollection(collectionName)

        when: 'clean up old database'
        run(mongoClient.getDatabase(databaseName).&dropDatabase) == null
        def names = run(mongoClient.&getDatabaseNames)

        then: 'Get Database Names'
        !names.contains(null)

        when: 'Create a collection and the created database is in the list'
        run(database.&createCollection, collectionName)
        def updatedNames = run(mongoClient.&getDatabaseNames)

        then: 'The database names should contain the database and be one bigger than before'
        updatedNames.contains(databaseName)
        updatedNames.size() == names.size() + 1

        when: 'The collection name should be in the collection names list'
        def collectionNames = run(database.&getCollectionNames)

        then:
        !collectionNames.contains(null)
        collectionNames.contains(collectionName)

        then: 'create an index'
        run(collection.&createIndex, new Document('test', 1)) == null

        then: 'has the newly created index'
        run(collection.&getIndexes)*.name.containsAll('_id_', 'test_1')

        then: 'drop the index'
        run(collection.&dropIndex, 'test_1') == null

        then: 'has a single index left "_id" '
        run(collection.&getIndexes).size == 1

        then: 'can rename the collection'
        def newCollectionName = 'newCollectionName'
        run(collection.&renameCollection, new MongoNamespace(databaseName, newCollectionName)) == null

        then: 'the new collection name is in the collection names list'
        !run(database.&getCollectionNames).contains(collectionName)
        run(database.&getCollectionNames).contains(newCollectionName)

        when:
        collection = database.getCollection(newCollectionName)

        then: 'drop the collection'
        run(collection.&dropCollection) == null

        then: 'there are no indexes'
        run(collection.&getIndexes).size == 0

        then: 'the collection name is no longer in the collectionNames list'
        !run(database.&getCollectionNames).contains(collectionName)
    }

    def run(operation, ... args) {
        def futureResultCallback = new FutureResultCallback()
        def opArgs = (args != null) ? args : []
        operation.call(*opArgs + futureResultCallback)
        futureResultCallback.get(10, SECONDS)
    }

}