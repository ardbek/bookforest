-- BookForest 테스트 DB 스키마
-- 출처: 운영과 동일하게 맞춘 로컬 MySQL 8.0.42 (2026-10-15 추출, 데이터 미포함)
-- 적용: Testcontainers MySQL 컨테이너의 /docker-entrypoint-initdb.d 에서 mysql 클라이언트로 실행
--       ngram 파서 지정이 /*!50100 ... */ 조건부 주석에 들어 있으므로 주석을 제거하는 스크립트 실행기로 실행하지 않음
-- 갱신: 운영 스키마 변경 시 아래 명령으로 다시 추출하고 AUTO_INCREMENT 값을 제거
--   mysqldump --no-data --skip-comments --no-tablespaces --skip-add-drop-table --skip-add-locks --single-transaction <database>


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `book_cover_enrichment` (
  `book_cover_enrichment_id` bigint NOT NULL AUTO_INCREMENT,
  `book_origin_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `last_attempt_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`book_cover_enrichment_id`),
  UNIQUE KEY `uq_book_cover_enrichment_origin` (`book_origin_id`),
  KEY `idx_book_cover_enrichment_claim` (`status`,`next_retry_at`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `book_origin` (
  `is_active` bit(1) NOT NULL,
  `published_date` date DEFAULT NULL,
  `book_origin_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `first_seen_at` datetime(6) DEFAULT NULL,
  `last_seen_at` datetime(6) DEFAULT NULL,
  `platform_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `isbn10_raw` varchar(50) DEFAULT NULL,
  `isbn13_raw` varchar(50) DEFAULT NULL,
  `published_date_raw` varchar(100) DEFAULT NULL,
  `platform_book_id` varchar(200) NOT NULL,
  `publisher_norm` varchar(300) DEFAULT NULL,
  `author_norm` varchar(500) DEFAULT NULL,
  `title_norm` varchar(500) NOT NULL,
  `book_url` varchar(1000) DEFAULT NULL,
  `cover_img_url` varchar(1000) DEFAULT NULL,
  `author_raw` text,
  `category_raw` text,
  `content_type` enum('AUDIOBOOK','EBOOK','UNKNOWN') NOT NULL,
  `description_raw` text,
  `publisher_raw` text,
  `title_raw` text NOT NULL,
  PRIMARY KEY (`book_origin_id`),
  UNIQUE KEY `uk_book_origin_platform_book` (`platform_id`,`content_type`,`platform_book_id`),
  FULLTEXT KEY `ft_book_origin_search` (`title_norm`,`author_norm`,`publisher_norm`) /*!50100 WITH PARSER `ngram` */ ,
  CONSTRAINT `FKhomhlbt7sanx3uxhj93xaogys` FOREIGN KEY (`platform_id`) REFERENCES `platform` (`platform_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `book_origin_category` (
  `book_origin_category_id` bigint NOT NULL AUTO_INCREMENT,
  `book_origin_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `platform_category_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`book_origin_category_id`),
  UNIQUE KEY `uk_origin_platform_category` (`book_origin_id`,`platform_category_id`),
  KEY `idx_boc_platform_category` (`platform_category_id`),
  CONSTRAINT `FK6isv2rxjq4jc6k81x5as63j2u` FOREIGN KEY (`book_origin_id`) REFERENCES `book_origin` (`book_origin_id`),
  CONSTRAINT `FKcgojfm1qkaaj272utxpx2f6l8` FOREIGN KEY (`platform_category_id`) REFERENCES `platform_category` (`platform_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collection_run` (
  `fail_count` int NOT NULL,
  `success_count` int NOT NULL,
  `total_count` int NOT NULL,
  `collection_run_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `library_id` bigint DEFAULT NULL,
  `platform_id` bigint NOT NULL,
  `started_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `error_message` text,
  `status` enum('FAILED','PARTIAL_SUCCESS','QUEUED','RUNNING','SUCCESS','CANCELLED') NOT NULL,
  `target_type` enum('CATEGORY','LIBRARY_BOOK','LIBRARY_LIST','SUBSCRIPTION_BOOK') NOT NULL,
  `batch_id` varchar(32) DEFAULT NULL,
  `requested_pages` int DEFAULT NULL,
  `requested_categories` text,
  `requested_library_ids` text,
  `incremental` tinyint(1) NOT NULL DEFAULT '0',
  `crawled_pages` int DEFAULT '0',
  `duplicated_count` int DEFAULT '0',
  `triggered_by` varchar(50) DEFAULT NULL,
  `error_location` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`collection_run_id`),
  KEY `idx_collection_run_platform_started` (`platform_id`,`started_at`),
  KEY `FKfabbrowl6qsdde38pklj1thlv` (`library_id`),
  KEY `idx_collection_run_status` (`status`),
  KEY `idx_collection_run_created_at_desc` (`created_at` DESC),
  CONSTRAINT `FKeag6rhghd317cmotr8m9hrlk8` FOREIGN KEY (`platform_id`) REFERENCES `platform` (`platform_id`),
  CONSTRAINT `FKfabbrowl6qsdde38pklj1thlv` FOREIGN KEY (`library_id`) REFERENCES `library` (`library_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collection_run_failure` (
  `collection_run_failure_id` bigint NOT NULL AUTO_INCREMENT,
  `collection_run_id` bigint NOT NULL,
  `library_id` bigint DEFAULT NULL,
  `category_code` longtext,
  `page_no` bigint DEFAULT NULL,
  `http_status` bigint DEFAULT NULL,
  `error_class` longtext,
  `error_message` longtext,
  `retryable` tinyint(1) DEFAULT NULL,
  `circuit_state` longtext,
  `circuit_reason` longtext,
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`collection_run_failure_id`),
  KEY `idx_collection_run_failure_run_id_id` (`collection_run_id`,`collection_run_failure_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collection_run_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `collection_run_id` bigint NOT NULL,
  `level` longtext,
  `message` longtext,
  `created_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_collection_run_log_run_id_id` (`collection_run_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `favorite_book` (
  `book_origin_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `favorite_book_id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`favorite_book_id`),
  UNIQUE KEY `uk_favorite_book_user_origin` (`user_id`,`book_origin_id`),
  KEY `FK6w1e18o1ujedm65bhk7mikow` (`book_origin_id`),
  CONSTRAINT `FK4vdv9ugvl5qbhhtdp3ns6twoy` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FK6w1e18o1ujedm65bhk7mikow` FOREIGN KEY (`book_origin_id`) REFERENCES `book_origin` (`book_origin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `favorite_library` (
  `created_at` datetime(6) DEFAULT NULL,
  `favorite_library_id` bigint NOT NULL AUTO_INCREMENT,
  `library_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`favorite_library_id`),
  UNIQUE KEY `uk_favorite_library_user_library` (`user_id`,`library_id`),
  KEY `FK8c428rt1jn2l9rfwe6e6gx9nx` (`library_id`),
  CONSTRAINT `FK8c428rt1jn2l9rfwe6e6gx9nx` FOREIGN KEY (`library_id`) REFERENCES `library` (`library_id`),
  CONSTRAINT `FKnobwh7uy1u68eflwkm2k3w0eq` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `library` (
  `is_visible` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `library_id` bigint NOT NULL AUTO_INCREMENT,
  `platform_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `library_code` varchar(100) NOT NULL,
  `library_name` varchar(200) NOT NULL,
  `site_url` varchar(200) DEFAULT NULL,
  `library_description` tinytext,
  PRIMARY KEY (`library_id`),
  KEY `idx_library_platform_visible` (`platform_id`,`is_visible`),
  CONSTRAINT `FK62n9xvkb08umqfgua94vox602` FOREIGN KEY (`platform_id`) REFERENCES `platform` (`platform_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `library_book` (
  `book_origin_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `first_seen_at` datetime(6) DEFAULT NULL,
  `last_seen_at` datetime(6) DEFAULT NULL,
  `last_seen_run_id` bigint DEFAULT NULL,
  `library_book_id` bigint NOT NULL AUTO_INCREMENT,
  `library_id` bigint NOT NULL,
  `removed_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `book_url` varchar(1000) DEFAULT NULL,
  `cover_img_url` varchar(1000) DEFAULT NULL,
  `availability_status` enum('AVAILABLE','REMOVED','UNKNOWN') NOT NULL,
  PRIMARY KEY (`library_book_id`),
  UNIQUE KEY `uk_library_book_origin` (`library_id`,`book_origin_id`),
  KEY `FKiwbjtp7sgg9p7te6xy55rpns6` (`book_origin_id`),
  CONSTRAINT `FKiwbjtp7sgg9p7te6xy55rpns6` FOREIGN KEY (`book_origin_id`) REFERENCES `book_origin` (`book_origin_id`),
  CONSTRAINT `FKlohkf9ywj855nc5nsd0mxss2f` FOREIGN KEY (`library_id`) REFERENCES `library` (`library_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `library_list_checkpoint` (
  `provider` varchar(50) NOT NULL,
  `job_type` varchar(50) NOT NULL,
  `current_page` bigint DEFAULT NULL,
  `max_page` bigint DEFAULT NULL,
  `expected_total` bigint DEFAULT NULL,
  `raw_count` bigint DEFAULT NULL,
  `unique_count` bigint DEFAULT NULL,
  `duplicate_count` bigint DEFAULT NULL,
  `invalid_count` bigint DEFAULT NULL,
  `resolver_fail_count` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `next_retry_at` datetime(3) DEFAULT NULL,
  `last_error_class` varchar(50) DEFAULT NULL,
  `last_error_message` text,
  `created_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`provider`,`job_type`),
  KEY `idx_library_list_checkpoint_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `platform` (
  `supports_library` bit(1) NOT NULL,
  `supports_subscription` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `platform_id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `platform_code` varchar(255) DEFAULT NULL,
  `platform_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`platform_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `platform_category` (
  `depth` int NOT NULL,
  `is_active` bit(1) NOT NULL,
  `mapping_confidence` decimal(3,2) DEFAULT NULL,
  `sort_order` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `mapped_at` datetime(6) DEFAULT NULL,
  `parent_platform_category_id` bigint DEFAULT NULL,
  `platform_category_id` bigint NOT NULL AUTO_INCREMENT,
  `platform_id` bigint NOT NULL,
  `standard_category_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `category_code` varchar(100) NOT NULL,
  `mapped_by` varchar(100) DEFAULT NULL,
  `category_name` varchar(200) NOT NULL,
  `category_path` varchar(1000) DEFAULT NULL,
  `mapping_status` enum('AUTO_MAPPED','CONFIRMED','IGNORED','UNMAPPED') NOT NULL,
  PRIMARY KEY (`platform_category_id`),
  UNIQUE KEY `uk_platform_category_identity` (`platform_id`,`category_code`,`category_name`),
  KEY `idx_pc_standard` (`standard_category_id`),
  KEY `idx_pc_mapping_status` (`mapping_status`,`platform_id`),
  KEY `FK16cc99gq0qy3h6vo562wq1w1b` (`parent_platform_category_id`),
  CONSTRAINT `FK16cc99gq0qy3h6vo562wq1w1b` FOREIGN KEY (`parent_platform_category_id`) REFERENCES `platform_category` (`platform_category_id`),
  CONSTRAINT `FK94u7y26w2thp8t8hvl3vk9tik` FOREIGN KEY (`standard_category_id`) REFERENCES `standard_category` (`standard_category_id`),
  CONSTRAINT `FKnlroj6uwrx38ek68c3fbdc5ye` FOREIGN KEY (`platform_id`) REFERENCES `platform` (`platform_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `social_account` (
  `created_at` datetime(6) DEFAULT NULL,
  `social_account_id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `nickname` varchar(100) DEFAULT NULL,
  `provider_subject` varchar(200) NOT NULL,
  `profile_image_url` varchar(1000) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `social_provider` enum('KAKAO','NAVER') NOT NULL,
  PRIMARY KEY (`social_account_id`),
  UNIQUE KEY `uk_social_provider_subject` (`social_provider`,`provider_subject`),
  KEY `FKqg7naq00cg9s76a2tljlyafn6` (`user_id`),
  CONSTRAINT `FKqg7naq00cg9s76a2tljlyafn6` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `social_oauth_credential` (
  `access_token_expires_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `social_account_id` bigint NOT NULL,
  `social_oauth_credential_id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `access_token_ciphertext` varchar(4096) DEFAULT NULL,
  `refresh_token_ciphertext` varchar(4096) DEFAULT NULL,
  PRIMARY KEY (`social_oauth_credential_id`),
  UNIQUE KEY `uk_social_oauth_credential_account` (`social_account_id`),
  CONSTRAINT `fk_social_oauth_credential_social_account` FOREIGN KEY (`social_account_id`) REFERENCES `social_account` (`social_account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `social_unlink_task` (
  `retry_count` int NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `social_unlink_task_id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `last_error_code` varchar(100) DEFAULT NULL,
  `provider_subject` varchar(200) NOT NULL,
  `provider_token_ciphertext` varchar(4096) DEFAULT NULL,
  `provider_token_type` enum('ACCESS_TOKEN','REFRESH_TOKEN') DEFAULT NULL,
  `social_provider` enum('KAKAO','NAVER') NOT NULL,
  `status` enum('FAILED','PENDING','PROCESSING','RETRY_WAITING','SUCCEEDED') NOT NULL,
  PRIMARY KEY (`social_unlink_task_id`),
  KEY `idx_social_unlink_task_retry` (`status`,`next_retry_at`),
  KEY `idx_social_unlink_task_provider_subject` (`social_provider`,`provider_subject`,`status`),
  KEY `idx_social_unlink_task_processing_timeout` (`status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `standard_category` (
  `depth` int NOT NULL,
  `is_active` bit(1) NOT NULL,
  `sort_order` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `parent_category_id` bigint DEFAULT NULL,
  `standard_category_id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `category_code` varchar(100) NOT NULL,
  `category_name` varchar(200) NOT NULL,
  `category_path` varchar(500) DEFAULT NULL,
  `aliases` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`standard_category_id`),
  UNIQUE KEY `uk_standard_category_code` (`category_code`),
  KEY `FK5njge8f9fx1e6wvfvmcd3u4il` (`parent_category_id`),
  CONSTRAINT `FK5njge8f9fx1e6wvfvmcd3u4il` FOREIGN KEY (`parent_category_id`) REFERENCES `standard_category` (`standard_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subscription_book` (
  `book_origin_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `first_seen_at` datetime(6) DEFAULT NULL,
  `last_seen_at` datetime(6) DEFAULT NULL,
  `last_seen_run_id` bigint DEFAULT NULL,
  `removed_at` datetime(6) DEFAULT NULL,
  `subscription_book_id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `book_url` varchar(1000) DEFAULT NULL,
  `cover_img_url` varchar(1000) DEFAULT NULL,
  `availability_status` enum('AVAILABLE','REMOVED','UNKNOWN') NOT NULL,
  PRIMARY KEY (`subscription_book_id`),
  UNIQUE KEY `uk_subscription_book_origin` (`book_origin_id`),
  CONSTRAINT `FKd1qu43k575nlmndp2191ggtns` FOREIGN KEY (`book_origin_id`) REFERENCES `book_origin` (`book_origin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `withdrawn` datetime(6) DEFAULT NULL,
  `display_name` varchar(100) DEFAULT NULL,
  `profile_image_url` varchar(1000) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `role` enum('ROLE_ADMIN','ROLE_USER') NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

