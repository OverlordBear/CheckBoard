/*
 Navicat Premium Data Transfer

 Source Server         : oa测试数据库
 Source Server Type    : MySQL
 Source Server Version : 80034
 Source Host           : 10.101.32.243:3306
 Source Schema         : ecology

 Target Server Type    : MySQL
 Target Server Version : 80034
 File Encoding         : 65001

 Date: 11/11/2026 14:20:32
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for edc_uf_table1109
-- ----------------------------
DROP TABLE IF EXISTS `edc_uf_table1109`;
CREATE TABLE `edc_uf_table1109`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `taskid` int NULL DEFAULT NULL,
  `ip` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `uuid` varchar(36) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `wxopenid` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `wxheadimgurl` varchar(300) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `wxnickname` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `wxsex` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `wxcountry` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `wxprovince` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `wxcity` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `clientdevice` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `clientos` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `clientbrowser` varchar(80) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `remark` varchar(2000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `requestId` int NULL DEFAULT NULL,
  `formmodeid` int NULL DEFAULT NULL,
  `modedatacreater` int NULL DEFAULT NULL,
  `modedatacreatertype` int NULL DEFAULT NULL,
  `modedatacreatedate` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `modedatacreatetime` varchar(8) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `modedatamodifier` int NULL DEFAULT NULL,
  `modedatamodifydatetime` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field001` int NULL DEFAULT NULL,
  `field002` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field003` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field004` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field005` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field006` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field007` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field008` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field009` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field010` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field011` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field012` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field013` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field014` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field015` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field016` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field017` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field018` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field019` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field020` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field021` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field022` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field023` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field024` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field025` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field026` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field027` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field028` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field029` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field030` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `field031` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `form_biz_id` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `MODEUUID` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `uf_ind_1793863054599`(`MODEUUID` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 41 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of edc_uf_table1109
-- ----------------------------
INSERT INTO `edc_uf_table1109` VALUES (34, NULL, '36.26.239.174', '0c330bfe46484d2cac21f4b7385d2c4b', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'Android', 'Android 16', 'Chrome Mobile 155.0.0.0', NULL, NULL, 683, 1, NULL, '2026-11-09', '10:32:31', NULL, NULL, 18882, '2026-11-09', '10:31', '0', '0', NULL, NULL, NULL, NULL, '氩弧焊机', NULL, NULL, '1', '1', '0', '0', '0', '0', '0', '0', '0', NULL, '0', '测试', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `edc_uf_table1109` VALUES (35, NULL, '36.26.239.174', 'cc858d0c6cc04e81a63644644bb5bdfa', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'Android', 'Android 16', 'Chrome Mobile 155.0.0.0', NULL, NULL, 683, 1, NULL, '2026-11-09', '10:44:17', NULL, NULL, 40, '2026-11-09', '10:43', '0', '0', NULL, NULL, NULL, NULL, '氩弧焊机', NULL, NULL, '1', '1', '0', '0', '0', '0', '0', '0', '0', NULL, '1', '测试异常', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `edc_uf_table1109` VALUES (36, NULL, '36.26.239.174', '9e9c3ec06f004d10982e38a854d75206', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'Android', 'Android 16', 'Chrome Mobile 155.0.0.0', NULL, NULL, 683, 1, NULL, '2026-11-09', '11:04:13', NULL, NULL, 18882, '2026-11-09', '11:03', '0', '0', NULL, NULL, NULL, NULL, '氩弧焊机', NULL, NULL, '1', '1', '0', '0', '0', '0', '0', '0', '0', NULL, '1', '1', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `edc_uf_table1109` VALUES (40, NULL, '36.26.239.174', '514c0b5e18c045818852d80418be8da9', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'PC', 'Windows 10', 'Chrome 15 155.0.0.0', NULL, NULL, 683, 1, NULL, '2026-11-09', '16:30:23', NULL, NULL, 20615, '2026-11-09', '16:30', '0', '0', NULL, NULL, NULL, NULL, '氩弧焊机', NULL, NULL, '0', '0', '0', '0', '0', '0', '0', '0', '0', NULL, '1', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);

SET FOREIGN_KEY_CHECKS = 1;
