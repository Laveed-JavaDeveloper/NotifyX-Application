# <img src="https://img.icons8.com/wired/64/000000/lightning-bolt.png" width="35" valign="middle"/> NotifyX — High-Throughput Event-Driven Notification Engine

[![Java Version](https://img.shields.io/badge/Java-17%20%2F%2021-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 📌 Project Overview

**NotifyX** is an enterprise-grade, asynchronous, event-driven notification engine built to handle high-velocity alerting traffic without degrading system performance. Inspired by production architectures at companies like Uber and Amazon, NotifyX completely decouples notification ingestion from actual delivery. 

When a client application triggers an alert (Email, SMS, or In-App push), NotifyX instantly validates the request, runs it through an algorithmic rate-limiter, drops it into a message queue, and returns an immediate response to the client. A resilient cluster of background workers then consumes, formats, and dispatches the messages reliably.

## 🏗️ Core Architectural Features

**Asynchronous Processing (Event-Driven Architecture): Utilizes a dedicated message broker/queue to process notifications out-of-band, eliminating network latency blocking during third-party API invocation.
**Algorithmic Token-Bucket Rate Limiter: Protects downstream delivery services from spamming and traffic surges by enforcing strict per-user/per-minute API ingestion throttles.
**Resilient Failover & Dead Letter Queue (DLQ): Implements automated retry logic with exponential backoff for transient network errors. Permanently failing payloads are isolated in a DLQ for manual auditing without stalling the primary pipeline.
**Dynamic Template Engine: Decouples message content from application logic. Supports complex JSON request payloads to compile string templates dynamically at runtime (e.g., parsing `"Hello {name}"` contextually).

---

## 🛠️ Tech Stack & Ecosystem

**Backend Core: Java 17+, Spring Boot (Web, Data JPA)
**Messaging Pipeline: Apache Kafka / RabbitMQ *(or Java Concurrent BlockingQueues for light deployment)*
**Data Tier: Oracle SQL / PostgreSQL (Relational persistence for audit logging & template schemas)
**Caching & State: Redis (Fast lookup for rate-limit tokens and session states)
**Testing Suite: JUnit 5, Mockito
**Build Tool: Maven

---

## 💾 Core Database Schema Overview

The application relies on a highly structured relational schema to maintain data integrity and detailed audit trails across state transitions:

```sql
-- Track message delivery states
CREATE TABLE notification_logs (
    id VARCHAR2(50) PRIMARY KEY,
    recipient VARCHAR2(100) NOT NULL,
    template_id VARCHAR2(50),
    status VARCHAR2(20) CHECK (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED_RETRYING', 'DLQ')),
    retry_count NUMBER(2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
```
