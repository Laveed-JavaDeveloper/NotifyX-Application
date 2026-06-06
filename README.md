# 🚀 NotifyX | High-Throughput Event-Driven Notification Engine

![Java](https://img.shields.io/badge/Java_17+-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Kafka/RabbitMQ](https://img.shields.io/badge/Messaging-Kafka_%2F_RabbitMQ-6DB33F?style=for-the-badge&logo=apache-kafka&logoColor=white)
![Data Tier](https://img.shields.io/badge/Database-PostgreSQL_%2F_Oracle-4479A1?style=for-the-badge&logo=postgresql&logoColor=white)

## 📖 Overview
**NotifyX** is an enterprise-grade, asynchronous, event-driven notification engine built to handle high-velocity alerting traffic without degrading system performance. Inspired by highly scalable production architectures, NotifyX completely decouples notification ingestion from actual delivery, allowing client applications to receive instant responses while a resilient background worker layer handles reliable processing and dispatching.

## ✨ Core Architectural Features
* **⚡ Event-Driven Decoupling:** Processes multi-channel alerts (Email, SMS, In-App) out-of-band using message brokers, eliminating network latency blocks during third-party API invocations.
* **🛡️ Algorithmic Token-Bucket Rate Limiter:** Protects downstream delivery services from traffic surges by enforcing strict per-user/per-minute API ingestion throttles backed by fast Redis lookups.
* **🔄 Fault Tolerance & Dead Letter Queue (DLQ):** Implements automated retry logic with exponential backoff for transient errors. Permanently failing payloads are securely isolated in a DLQ for manual auditing without stalling the primary pipeline.
* **🎨 Dynamic Template Engine:** Decouples message content from core application logic, parsing complex JSON request payloads to compile string templates dynamically at runtime.

## 🛠️ Tech Stack & Ecosystem
* **Core Backend:** Java 17+, Spring Boot (Web, Data JPA)
* **Messaging Pipeline:** Apache Kafka / RabbitMQ *(or Java Concurrent BlockingQueues for light deployment)*
* **Data Tier:** Oracle SQL / PostgreSQL (Relational persistence for audit logging & template schemas)
* **Caching & State:** Redis (Token-bucket tracking and fast lookups)
* **Testing Suite:** JUnit 5, Mockito
* **Build Automation:** Apache Maven

## 💾 Core Database Schema
The application relies on a highly structured relational schema to maintain data integrity and detailed audit trails across state transitions:

```sql
-- Track message delivery states across the processing lifecycle
CREATE TABLE notification_logs (
    id VARCHAR2(50) PRIMARY KEY,
    recipient VARCHAR2(100) NOT NULL,
    template_id VARCHAR2(50),
    status VARCHAR2(20) CHECK (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED_RETRYING', 'DLQ')),
    retry_count NUMBER(2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
