/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.cluster.model;

import io.strimzi.api.kafka.model.CertSecretSource;
import io.strimzi.api.kafka.model.CertSecretSourceBuilder;
import io.strimzi.api.kafka.model.KafkaAuthorization;
import io.strimzi.api.kafka.model.KafkaAuthorizationKeycloakBuilder;
import io.strimzi.api.kafka.model.KafkaAuthorizationSimpleBuilder;
import io.strimzi.api.kafka.model.Rack;
import io.strimzi.api.kafka.model.listener.IngressListenerBrokerConfiguration;
import io.strimzi.api.kafka.model.listener.IngressListenerBrokerConfigurationBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationOAuth;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationOAuthBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.strimzi.api.kafka.model.listener.KafkaListenersBuilder;
import io.strimzi.api.kafka.model.storage.EphemeralStorageBuilder;
import io.strimzi.api.kafka.model.storage.JbodStorageBuilder;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorageBuilder;
import io.strimzi.api.kafka.model.storage.SingleVolumeStorage;
import io.strimzi.api.kafka.model.storage.Storage;
import io.strimzi.kafka.oauth.server.ServerConfig;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.strimzi.operator.cluster.model.KafkaBrokerConfigurationBuilderTest.IsEquivalent.isEquivalent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class KafkaBrokerConfigurationBuilderTest {
    @Test
    public void testBrokerId()  {
        String configuration = new KafkaBrokerConfigurationBuilder()
                .withBrokerId()
                .build();

        assertThat(configuration, isEquivalent("broker.id=${STRIMZI_BROKER_ID}"));
    }

    @Test
    public void testNoRackAwareness()  {
        String configuration = new KafkaBrokerConfigurationBuilder()
                .withRackId(null)
                .build();

        assertThat(configuration, isEquivalent(""));
    }

    @Test
    public void testRackId()  {
        String configuration = new KafkaBrokerConfigurationBuilder()
                .withRackId(new Rack("failure-domain.kubernetes.io/zone"))
                .build();

        assertThat(configuration, isEquivalent("broker.rack=${STRIMZI_RACK_ID}"));
    }

    @Test
    public void testRackAndBrokerId()  {
        String configuration = new KafkaBrokerConfigurationBuilder()
                .withBrokerId()
                .withRackId(new Rack("failure-domain.kubernetes.io/zone"))
                .build();

        assertThat(configuration, isEquivalent("broker.id=${STRIMZI_BROKER_ID}\n" +
                                                                "broker.rack=${STRIMZI_RACK_ID}"));
    }

    @Test
    public void testZookeeperConfig()  {
        String configuration = new KafkaBrokerConfigurationBuilder()
                .withZookeeper()
                .build();

        assertThat(configuration, isEquivalent("zookeeper.connect=localhost:2181"));
    }

    @Test
    public void testNoAuthorization()  {
        String configuration = new KafkaBrokerConfigurationBuilder()
                .withAuthorization("my-cluster", null)
                .build();

        assertThat(configuration, isEquivalent(""));
    }

    @Test
    public void testSimpleAuthorizationWithSuperUsers()  {
        KafkaAuthorization auth = new KafkaAuthorizationSimpleBuilder()
                .addToSuperUsers("jakub", "CN=kuba")
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withAuthorization("my-cluster", auth)
                .build();

        assertThat(configuration, isEquivalent("authorizer.class.name=kafka.security.auth.SimpleAclAuthorizer\n" +
                "super.users=User:CN=my-cluster-kafka,O=io.strimzi;User:CN=my-cluster-entity-operator,O=io.strimzi;User:CN=my-cluster-kafka-exporter,O=io.strimzi;User:CN=cluster-operator,O=io.strimzi;User:jakub;User:CN=kuba"));
    }

    @Test
    public void testSimpleAuthorizationWithoutSuperUsers()  {
        KafkaAuthorization auth = new KafkaAuthorizationSimpleBuilder()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withAuthorization("my-cluster", auth)
                .build();

        assertThat(configuration, isEquivalent("authorizer.class.name=kafka.security.auth.SimpleAclAuthorizer\n" +
                "super.users=User:CN=my-cluster-kafka,O=io.strimzi;User:CN=my-cluster-entity-operator,O=io.strimzi;User:CN=my-cluster-kafka-exporter,O=io.strimzi;User:CN=cluster-operator,O=io.strimzi"));
    }

    @Test
    public void testKeycloakAuthorization() {
        CertSecretSource cert = new CertSecretSourceBuilder()
                .withNewSecretName("my-secret")
                .withNewCertificate("my.crt")
                .build();

        KafkaAuthorization auth = new KafkaAuthorizationKeycloakBuilder()
                .withTokenEndpointUri("http://token-endpoint-uri")
                .withClientId("my-client-id")
                .withDelegateToKafkaAcls(false)
                .withTlsTrustedCertificates(cert)
                .withDisableTlsHostnameVerification(true)
                .addToSuperUsers("giada", "CN=paccu")
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withAuthorization("my-cluster", auth)
                .build();

        assertThat(configuration, isEquivalent("authorizer.class.name=io.strimzi.kafka.oauth.server.authorizer.KeycloakRBACAuthorizer\n" +
                "principal.builder.class=io.strimzi.kafka.oauth.server.authorizer.JwtKafkaPrincipalBuilder\n" +
                "strimzi.authorization.token.endpoint.uri=http://token-endpoint-uri\n" +
                "strimzi.authorization.client.id=my-client-id\n" +
                "strimzi.authorization.delegate.to.kafka.acl=false\n" +
                "strimzi.authorization.kafka.cluster.name=my-cluster\n" +
                "strimzi.authorization.ssl.truststore.location=/tmp/kafka/authz-keycloak.truststore.p12\n" +
                "strimzi.authorization.ssl.truststore.password=${CERTS_STORE_PASSWORD}\n" +
                "strimzi.authorization.ssl.truststore.type=PKCS12\n" +
                "strimzi.authorization.ssl.secure.random.implementation=SHA1PRNG\n" +
                "strimzi.authorization.ssl.endpoint.identification.algorithm=\n" +
                "super.users=User:CN=my-cluster-kafka,O=io.strimzi;User:CN=my-cluster-entity-operator,O=io.strimzi;User:CN=my-cluster-kafka-exporter,O=io.strimzi;User:CN=cluster-operator,O=io.strimzi;User:giada;User:CN=paccu"));
    }

    @Test
    public void testNullUserConfiguration()  {
        String configuration = new KafkaBrokerConfigurationBuilder()
                .withUserConfiguration(null)
                .build();

        assertThat(configuration, isEquivalent(""));
    }

    @Test
    public void testEmptyUserConfiguration()  {
        Map<String, Object> userConfiguration = new HashMap<>();
        KafkaConfiguration kafkaConfiguration = new KafkaConfiguration(userConfiguration.entrySet());

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withUserConfiguration(kafkaConfiguration)
                .build();

        assertThat(configuration, isEquivalent(""));
    }

    @Test
    public void testUserConfiguration()  {
        Map<String, Object> userConfiguration = new HashMap<>();
        userConfiguration.put("auto.create.topics.enable", "false");
        userConfiguration.put("offsets.topic.replication.factor", 3);
        userConfiguration.put("transaction.state.log.replication.factor", 3);
        userConfiguration.put("transaction.state.log.min.isr", 2);

        KafkaConfiguration kafkaConfiguration = new KafkaConfiguration(userConfiguration.entrySet());

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withUserConfiguration(kafkaConfiguration)
                .build();

        assertThat(configuration, isEquivalent("auto.create.topics.enable=false\n" +
                                                            "offsets.topic.replication.factor=3\n" +
                                                            "transaction.state.log.replication.factor=3\n" +
                                                            "transaction.state.log.min.isr=2"));
    }

    @Test
    public void testEphemeralStorageLogDirs()  {
        Storage storage = new EphemeralStorageBuilder()
                .withNewSizeLimit("5Gi")
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withLogDirs(VolumeUtils.getDataVolumeMountPaths(storage, "/var/lib/kafka"))
                .build();

        assertThat(configuration, isEquivalent("log.dirs=/var/lib/kafka/data/kafka-log${STRIMZI_BROKER_ID}"));
    }

    @Test
    public void testPersistentStorageLogDirs()  {
        Storage storage = new PersistentClaimStorageBuilder()
                .withSize("1Ti")
                .withStorageClass("aws-ebs")
                .withDeleteClaim(true)
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withLogDirs(VolumeUtils.getDataVolumeMountPaths(storage, "/var/lib/kafka"))
                .build();

        assertThat(configuration, isEquivalent("log.dirs=/var/lib/kafka/data/kafka-log${STRIMZI_BROKER_ID}"));
    }

    @Test
    public void testJbodStorageLogDirs()  {
        SingleVolumeStorage vol1 = new PersistentClaimStorageBuilder()
                .withId(1)
                .withSize("1Ti")
                .withStorageClass("aws-ebs")
                .withDeleteClaim(true)
                .build();

        SingleVolumeStorage vol2 = new EphemeralStorageBuilder()
                .withId(2)
                .withNewSizeLimit("5Gi")
                .build();

        SingleVolumeStorage vol5 = new PersistentClaimStorageBuilder()
                .withId(5)
                .withSize("10Ti")
                .withStorageClass("aws-ebs")
                .withDeleteClaim(false)
                .build();

        Storage storage = new JbodStorageBuilder()
                .withVolumes(vol1, vol2, vol5)
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withLogDirs(VolumeUtils.getDataVolumeMountPaths(storage, "/var/lib/kafka"))
                .build();

        assertThat(configuration, isEquivalent("log.dirs=/var/lib/kafka/data-1/kafka-log${STRIMZI_BROKER_ID},/var/lib/kafka/data-2/kafka-log${STRIMZI_BROKER_ID},/var/lib/kafka/data-5/kafka-log${STRIMZI_BROKER_ID}"));
    }

    @Test
    public void testWithNoListeners()  {
        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", null)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091",
                "listener.security.protocol.map=REPLICATION-9091:SSL",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS"));
    }

    @Test
    public void testWithPlainListenersWithoutAuth()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewPlain()
                .endPlain()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,PLAIN-9092://0.0.0.0:9092",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,PLAIN-9092://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9092",
                "listener.security.protocol.map=REPLICATION-9091:SSL,PLAIN-9092:PLAINTEXT",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS"));
    }

    @Test
    public void testWithPlainListenersWithSaslAuth()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewPlain()
                    .withNewKafkaListenerAuthenticationScramSha512Auth()
                    .endKafkaListenerAuthenticationScramSha512Auth()
                .endPlain()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,PLAIN-9092://0.0.0.0:9092",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,PLAIN-9092://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9092",
                "listener.security.protocol.map=REPLICATION-9091:SSL,PLAIN-9092:SASL_PLAINTEXT",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.plain-9092.scram-sha-512.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required;",
                "listener.name.plain-9092.sasl.enabled.mechanisms=SCRAM-SHA-512"));
    }

    @Test
    public void testWithTlsListenersWithoutAuth()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewTls()
                .endTls()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,TLS-9093://0.0.0.0:9093",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,TLS-9093://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9093",
                "listener.security.protocol.map=REPLICATION-9091:SSL,TLS-9093:SSL",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.tls-9093.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.tls-9093.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.tls-9093.ssl.keystore.type=PKCS12"));
    }

    @Test
    public void testWithTlsListenersWithTlsAuth()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewTls()
                    .withNewKafkaListenerAuthenticationTlsAuth()
                    .endKafkaListenerAuthenticationTlsAuth()
                .endTls()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,TLS-9093://0.0.0.0:9093",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,TLS-9093://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9093",
                "listener.security.protocol.map=REPLICATION-9091:SSL,TLS-9093:SSL",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.tls-9093.ssl.client.auth=required",
                "listener.name.tls-9093.ssl.truststore.location=/tmp/kafka/clients.truststore.p12",
                "listener.name.tls-9093.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.tls-9093.ssl.truststore.type=PKCS12",
                "listener.name.tls-9093.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.tls-9093.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.tls-9093.ssl.keystore.type=PKCS12"));
    }

    @Test
    public void testWithTlsListenersWithCustomCerts()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewTls()
                    .withNewConfiguration()
                        .withNewBrokerCertChainAndKey()
                            .withNewSecretName("my-secret")
                            .withNewKey("my.key")
                            .withNewCertificate("my.crt")
                        .endBrokerCertChainAndKey()
                    .endConfiguration()
                .endTls()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,TLS-9093://0.0.0.0:9093",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,TLS-9093://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9093",
                "listener.security.protocol.map=REPLICATION-9091:SSL,TLS-9093:SSL",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.tls-9093.ssl.keystore.location=/tmp/kafka/custom-tls-9093.keystore.p12",
                "listener.name.tls-9093.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.tls-9093.ssl.keystore.type=PKCS12"));
    }

    @Test
    public void testWithExternalRouteListenersWithoutAuth()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewKafkaListenerExternalRoute()
                .endKafkaListenerExternalRoute()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,EXTERNAL-9094://0.0.0.0:9094",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,EXTERNAL-9094://${STRIMZI_EXTERNAL_9094_ADVERTISED_HOSTNAME}:${STRIMZI_EXTERNAL_9094_ADVERTISED_PORT}",
                "listener.security.protocol.map=REPLICATION-9091:SSL,EXTERNAL-9094:SSL",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.external-9094.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.external-9094.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.external-9094.ssl.keystore.type=PKCS12"));
    }

    @Test
    public void testWithExternalRouteListenersWithTlsAuth()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewKafkaListenerExternalRoute()
                    .withNewKafkaListenerAuthenticationTlsAuth()
                    .endKafkaListenerAuthenticationTlsAuth()
                .endKafkaListenerExternalRoute()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,EXTERNAL-9094://0.0.0.0:9094",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,EXTERNAL-9094://${STRIMZI_EXTERNAL_9094_ADVERTISED_HOSTNAME}:${STRIMZI_EXTERNAL_9094_ADVERTISED_PORT}",
                "listener.security.protocol.map=REPLICATION-9091:SSL,EXTERNAL-9094:SSL",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.external-9094.ssl.client.auth=required",
                "listener.name.external-9094.ssl.truststore.location=/tmp/kafka/clients.truststore.p12",
                "listener.name.external-9094.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.external-9094.ssl.truststore.type=PKCS12",
                "listener.name.external-9094.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.external-9094.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.external-9094.ssl.keystore.type=PKCS12"));
    }

    @Test
    public void testWithExternalRouteListenersWithSaslAuth()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewKafkaListenerExternalRoute()
                    .withNewKafkaListenerAuthenticationScramSha512Auth()
                    .endKafkaListenerAuthenticationScramSha512Auth()
                .endKafkaListenerExternalRoute()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,EXTERNAL-9094://0.0.0.0:9094",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,EXTERNAL-9094://${STRIMZI_EXTERNAL_9094_ADVERTISED_HOSTNAME}:${STRIMZI_EXTERNAL_9094_ADVERTISED_PORT}",
                "listener.security.protocol.map=REPLICATION-9091:SSL,EXTERNAL-9094:SASL_SSL",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.external-9094.scram-sha-512.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required;",
                "listener.name.external-9094.sasl.enabled.mechanisms=SCRAM-SHA-512",
                "listener.name.external-9094.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.external-9094.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.external-9094.ssl.keystore.type=PKCS12"));
    }

    @Test
    public void testWithExternalRouteListenersWithCustomCerts()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewKafkaListenerExternalRoute()
                    .withNewConfiguration()
                        .withNewBrokerCertChainAndKey()
                            .withNewSecretName("my-secret")
                            .withNewKey("my.key")
                            .withNewCertificate("my.crt")
                        .endBrokerCertChainAndKey()
                    .endConfiguration()
                .endKafkaListenerExternalRoute()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,EXTERNAL-9094://0.0.0.0:9094",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,EXTERNAL-9094://${STRIMZI_EXTERNAL_9094_ADVERTISED_HOSTNAME}:${STRIMZI_EXTERNAL_9094_ADVERTISED_PORT}",
                "listener.security.protocol.map=REPLICATION-9091:SSL,EXTERNAL-9094:SSL",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.external-9094.ssl.keystore.location=/tmp/kafka/custom-external-9094.keystore.p12",
                "listener.name.external-9094.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.external-9094.ssl.keystore.type=PKCS12"));
    }

    @Test
    public void testWithExternalListenersLoadBalancerWithTls()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewKafkaListenerExternalLoadBalancer()
                .endKafkaListenerExternalLoadBalancer()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,EXTERNAL-9094://0.0.0.0:9094",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,EXTERNAL-9094://${STRIMZI_EXTERNAL_9094_ADVERTISED_HOSTNAME}:${STRIMZI_EXTERNAL_9094_ADVERTISED_PORT}",
                "listener.security.protocol.map=REPLICATION-9091:SSL,EXTERNAL-9094:SSL",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.external-9094.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.external-9094.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.external-9094.ssl.keystore.type=PKCS12"));
    }

    @Test
    public void testWithExternalListenersLoadBalancerWithoutTls()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewKafkaListenerExternalLoadBalancer()
                    .withTls(false)
                .endKafkaListenerExternalLoadBalancer()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,EXTERNAL-9094://0.0.0.0:9094",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,EXTERNAL-9094://${STRIMZI_EXTERNAL_9094_ADVERTISED_HOSTNAME}:${STRIMZI_EXTERNAL_9094_ADVERTISED_PORT}",
                "listener.security.protocol.map=REPLICATION-9091:SSL,EXTERNAL-9094:PLAINTEXT",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS"));
    }

    @Test
    public void testWithExternalListenersNodePortWithTls()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewKafkaListenerExternalNodePort()
                .endKafkaListenerExternalNodePort()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,EXTERNAL-9094://0.0.0.0:9094",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,EXTERNAL-9094://${STRIMZI_EXTERNAL_9094_ADVERTISED_HOSTNAME}:${STRIMZI_EXTERNAL_9094_ADVERTISED_PORT}",
                "listener.security.protocol.map=REPLICATION-9091:SSL,EXTERNAL-9094:SSL",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.external-9094.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.external-9094.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.external-9094.ssl.keystore.type=PKCS12"));
    }

    @Test
    public void testWithExternalListenersNodePortWithoutTls()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewKafkaListenerExternalNodePort()
                    .withTls(false)
                .endKafkaListenerExternalNodePort()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,EXTERNAL-9094://0.0.0.0:9094",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,EXTERNAL-9094://${STRIMZI_EXTERNAL_9094_ADVERTISED_HOSTNAME}:${STRIMZI_EXTERNAL_9094_ADVERTISED_PORT}",
                "listener.security.protocol.map=REPLICATION-9091:SSL,EXTERNAL-9094:PLAINTEXT",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS"));
    }

    @Test
    public void testWithExternalListenersIngress()  {
        IngressListenerBrokerConfiguration broker = new IngressListenerBrokerConfigurationBuilder()
                .withBroker(0)
                .withNewHost("broker-0.mytld.com")
                .build();

        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewKafkaListenerExternalIngress()
                    .withNewConfiguration()
                        .withNewBootstrap()
                            .withNewHost("bootstrap.mytld.com")
                        .endBootstrap()
                        .withBrokers(broker)
                    .endConfiguration()
                    .withNewIngressClass("nginx-ingress")
                .endKafkaListenerExternalIngress()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,EXTERNAL-9094://0.0.0.0:9094",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,EXTERNAL-9094://${STRIMZI_EXTERNAL_9094_ADVERTISED_HOSTNAME}:${STRIMZI_EXTERNAL_9094_ADVERTISED_PORT}",
                "listener.security.protocol.map=REPLICATION-9091:SSL,EXTERNAL-9094:SSL",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.external-9094.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.external-9094.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.external-9094.ssl.keystore.type=PKCS12"));
    }

    @Test
    public void testOauthConfiguration()  {
        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewPlain()
                    .withNewKafkaListenerAuthenticationOAuth()
                        .withNewValidIssuerUri("http://valid-issuer")
                        .withNewJwksEndpointUri("http://jwks")
                        .withEnableECDSA(true)
                        .withNewUserNameClaim("preferred_username")
                    .endKafkaListenerAuthenticationOAuth()
                .endPlain()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,PLAIN-9092://0.0.0.0:9092",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,PLAIN-9092://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9092",
                "listener.security.protocol.map=REPLICATION-9091:SSL,PLAIN-9092:SASL_PLAINTEXT",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.plain-9092.oauthbearer.sasl.server.callback.handler.class=io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler",
                "listener.name.plain-9092.oauthbearer.sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required unsecuredLoginStringClaim_sub=\"thePrincipalName\" oauth.valid.issuer.uri=\"http://valid-issuer\" oauth.jwks.endpoint.uri=\"http://jwks\" oauth.crypto.provider.bouncycastle=\"true\" oauth.username.claim=\"preferred_username\";",
                "listener.name.plain-9092.sasl.enabled.mechanisms=OAUTHBEARER"));
    }

    @Test
    public void testOauthConfigurationWithTlsConfig()  {
        CertSecretSource cert = new CertSecretSourceBuilder()
                .withNewSecretName("my-secret")
                .withNewCertificate("my.crt")
                .build();

        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewPlain()
                        .withNewKafkaListenerAuthenticationOAuth()
                        .withNewValidIssuerUri("https://valid-issuer")
                        .withNewJwksEndpointUri("https://jwks")
                        .withEnableECDSA(true)
                        .withNewUserNameClaim("preferred_username")
                        .withDisableTlsHostnameVerification(true)
                        .withTlsTrustedCertificates(cert)
                    .endKafkaListenerAuthenticationOAuth()
                .endPlain()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,PLAIN-9092://0.0.0.0:9092",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,PLAIN-9092://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9092",
                "listener.security.protocol.map=REPLICATION-9091:SSL,PLAIN-9092:SASL_PLAINTEXT",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.plain-9092.oauthbearer.sasl.server.callback.handler.class=io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler",
                "listener.name.plain-9092.oauthbearer.sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required unsecuredLoginStringClaim_sub=\"thePrincipalName\" oauth.valid.issuer.uri=\"https://valid-issuer\" oauth.jwks.endpoint.uri=\"https://jwks\" oauth.crypto.provider.bouncycastle=\"true\" oauth.username.claim=\"preferred_username\" oauth.ssl.endpoint.identification.algorithm=\"\" oauth.ssl.truststore.location=\"/tmp/kafka/oauth-plain-9092.truststore.p12\" oauth.ssl.truststore.password=\"${CERTS_STORE_PASSWORD}\" oauth.ssl.truststore.type=\"PKCS12\";",
                "listener.name.plain-9092.sasl.enabled.mechanisms=OAUTHBEARER"));
    }

    @Test
    public void testOauthConfigurationWithClientSecret()  {
        CertSecretSource cert = new CertSecretSourceBuilder()
                .withNewSecretName("my-secret")
                .withNewCertificate("my.crt")
                .build();

        KafkaListeners listeners = new KafkaListenersBuilder()
                .withNewPlain()
                    .withNewKafkaListenerAuthenticationOAuth()
                        .withNewValidIssuerUri("https://valid-issuer")
                        .withNewIntrospectionEndpointUri("https://intro")
                        .withNewClientId("my-oauth-client")
                        .withNewClientSecret()
                            .withNewSecretName("my-secret")
                            .withKey("client-secret")
                        .endClientSecret()
                    .endKafkaListenerAuthenticationOAuth()
                .endPlain()
                .build();

        String configuration = new KafkaBrokerConfigurationBuilder()
                .withListeners("my-cluster", "my-namespace", listeners)
                .build();

        assertThat(configuration, isEquivalent("listener.name.replication-9091.ssl.keystore.location=/tmp/kafka/cluster.keystore.p12",
                "listener.name.replication-9091.ssl.keystore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.keystore.type=PKCS12",
                "listener.name.replication-9091.ssl.truststore.location=/tmp/kafka/cluster.truststore.p12",
                "listener.name.replication-9091.ssl.truststore.password=${CERTS_STORE_PASSWORD}",
                "listener.name.replication-9091.ssl.truststore.type=PKCS12",
                "listener.name.replication-9091.ssl.client.auth=required",
                "listeners=REPLICATION-9091://0.0.0.0:9091,PLAIN-9092://0.0.0.0:9092",
                "advertised.listeners=REPLICATION-9091://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9091,PLAIN-9092://my-cluster-kafka-${STRIMZI_BROKER_ID}.my-cluster-kafka-brokers.my-namespace.svc:9092",
                "listener.security.protocol.map=REPLICATION-9091:SSL,PLAIN-9092:SASL_PLAINTEXT",
                "inter.broker.listener.name=REPLICATION-9091",
                "sasl.enabled.mechanisms=",
                "ssl.secure.random.implementation=SHA1PRNG",
                "ssl.endpoint.identification.algorithm=HTTPS",
                "listener.name.plain-9092.oauthbearer.sasl.server.callback.handler.class=io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler",
                "listener.name.plain-9092.oauthbearer.sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required unsecuredLoginStringClaim_sub=\"thePrincipalName\" oauth.client.id=\"my-oauth-client\" oauth.valid.issuer.uri=\"https://valid-issuer\" oauth.introspection.endpoint.uri=\"https://intro\" oauth.client.secret=\"${STRIMZI_PLAIN_9092_OAUTH_CLIENT_SECRET}\";",
                "listener.name.plain-9092.sasl.enabled.mechanisms=OAUTHBEARER"));
    }

    @Test
    public void testOAuthOptions()  {
        KafkaListenerAuthenticationOAuth auth = new KafkaListenerAuthenticationOAuthBuilder()
                .withValidIssuerUri("http://valid-issuer")
                .withJwksEndpointUri("http://jwks-endpoint")
                .withIntrospectionEndpointUri("http://introspection-endpoint")
                .withJwksExpirySeconds(160)
                .withJwksRefreshSeconds(50)
                .withEnableECDSA(true)
                .withUserNameClaim("preferred_username")
                .withCheckAccessTokenType(false)
                .withClientId("my-kafka-id")
                .withAccessTokenIsJwt(false)
                .withDisableTlsHostnameVerification(true)
                .build();

        List<String> expectedOptions = new ArrayList<>(5);
        expectedOptions.add(String.format("%s=\"%s\"", ServerConfig.OAUTH_CLIENT_ID, "my-kafka-id"));
        expectedOptions.add(String.format("%s=\"%s\"", ServerConfig.OAUTH_VALID_ISSUER_URI, "http://valid-issuer"));
        expectedOptions.add(String.format("%s=\"%s\"", ServerConfig.OAUTH_JWKS_ENDPOINT_URI, "http://jwks-endpoint"));
        expectedOptions.add(String.format("%s=\"%d\"", ServerConfig.OAUTH_JWKS_REFRESH_SECONDS, 50));
        expectedOptions.add(String.format("%s=\"%d\"", ServerConfig.OAUTH_JWKS_EXPIRY_SECONDS, 160));
        expectedOptions.add(String.format("%s=\"%s\"", ServerConfig.OAUTH_CRYPTO_PROVIDER_BOUNCYCASTLE, true));
        expectedOptions.add(String.format("%s=\"%s\"", ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI, "http://introspection-endpoint"));
        expectedOptions.add(String.format("%s=\"%s\"", ServerConfig.OAUTH_USERNAME_CLAIM, "preferred_username"));
        expectedOptions.add(String.format("%s=\"%s\"", ServerConfig.OAUTH_ACCESS_TOKEN_IS_JWT, false));
        expectedOptions.add(String.format("%s=\"%s\"", ServerConfig.OAUTH_CHECK_ACCESS_TOKEN_TYPE, false));
        expectedOptions.add(String.format("%s=\"%s\"", ServerConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, ""));

        List<String> actualOptions = KafkaBrokerConfigurationBuilder.getOAuthOptions(auth);

        assertThat(actualOptions, is(equalTo(expectedOptions)));
    }

    static class IsEquivalent extends TypeSafeMatcher<String> {
        private List<String> expectedLines;

        public IsEquivalent(String expectedConfig) {
            super();
            this.expectedLines = getLinesWithoutCommentsAndEmptyLines(expectedConfig);
        }

        public IsEquivalent(List<String> expectedLines) {
            super();
            this.expectedLines = expectedLines;
        }

        @Override
        protected boolean matchesSafely(String config) {
            List<String> actualLines = getLinesWithoutCommentsAndEmptyLines(config);

            return expectedLines.containsAll(actualLines) && actualLines.containsAll(expectedLines);
        }

        private List<String> getLinesWithoutCommentsAndEmptyLines(String config) {
            List<String> allLines = Arrays.asList(config.split("\\r?\\n"));
            List<String> validLines = new ArrayList<>();

            for (String line : allLines)    {
                if (!line.startsWith("#") && !line.isEmpty())   {
                    validLines.add(line);
                }
            }

            return validLines;
        }

        private String getLinesAsString(List<String> configLines)   {
            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);

            for (String line : configLines) {
                writer.println(line);
            }

            return stringWriter.toString();
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(getLinesAsString(expectedLines));
        }

        public static Matcher<String> isEquivalent(String expectedConfig) {
            return new IsEquivalent(expectedConfig);
        }

        public static Matcher<String> isEquivalent(String... expectedLines) {
            return new IsEquivalent(Arrays.asList(expectedLines));
        }
    }
}
