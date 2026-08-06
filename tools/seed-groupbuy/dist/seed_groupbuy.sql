-- 由 tools/seed-groupbuy/gen.mjs 生成（每商家 2-3 个团购，1 秒杀 + 其余不限量）
-- 图片暂用店铺首图，SD 生成图后续替换
-- 生成时间 2026-08-05T07:56:15.847Z

INSERT INTO `tb_voucher` (`id`, `shop_id`, `title`, `sub_title`, `image`, `pay_value`, `actual_value`, `type`, `status`) VALUES
(1000000, 1, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://qcloud.dpfile.com/pc/jiclIsCKmOI2arxKN1Uf0Hx3PucIJH8q0QSz-Z8llzcN56-_QiKuOvyio1OOxsRtFoXqu0G3iT2T27qat3WhLVEuLYk00OmSS1IdNpm8K8sG4JN9RIm2mTKcbLtc2o2vfCF2ubeXzk49OsGrXt_KYDCngOyCwZK-s3fqawWswzk.jpg', 12800, 18800, 1, 1),
(1000001, 1, '单人豪华餐', '单人精选 · 主食+饮品', 'https://qcloud.dpfile.com/pc/jiclIsCKmOI2arxKN1Uf0Hx3PucIJH8q0QSz-Z8llzcN56-_QiKuOvyio1OOxsRtFoXqu0G3iT2T27qat3WhLVEuLYk00OmSS1IdNpm8K8sG4JN9RIm2mTKcbLtc2o2vfCF2ubeXzk49OsGrXt_KYDCngOyCwZK-s3fqawWswzk.jpg', 6800, 9800, 0, 1),
(1000002, 1, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://qcloud.dpfile.com/pc/jiclIsCKmOI2arxKN1Uf0Hx3PucIJH8q0QSz-Z8llzcN56-_QiKuOvyio1OOxsRtFoXqu0G3iT2T27qat3WhLVEuLYk00OmSS1IdNpm8K8sG4JN9RIm2mTKcbLtc2o2vfCF2ubeXzk49OsGrXt_KYDCngOyCwZK-s3fqawWswzk.jpg', 15800, 22800, 0, 1),
(1000003, 2, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://p0.meituan.net/bbia/c1870d570e73accbc9fee90b48faca41195272.jpg', 12800, 18800, 1, 1),
(1000004, 2, '单人豪华餐', '单人精选 · 主食+饮品', 'https://p0.meituan.net/bbia/c1870d570e73accbc9fee90b48faca41195272.jpg', 6800, 9800, 0, 1),
(1000005, 2, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://p0.meituan.net/bbia/c1870d570e73accbc9fee90b48faca41195272.jpg', 15800, 22800, 0, 1),
(1000006, 3, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://p0.meituan.net/biztone/694233_1619500156517.jpeg', 12800, 18800, 1, 1),
(1000007, 3, '单人豪华餐', '单人精选 · 主食+饮品', 'https://p0.meituan.net/biztone/694233_1619500156517.jpeg', 6800, 9800, 0, 1),
(1000008, 3, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://p0.meituan.net/biztone/694233_1619500156517.jpeg', 15800, 22800, 0, 1),
(1000009, 4, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://img.meituan.net/msmerchant/232f8fdf09050838bd33fb24e79f30f9606056.jpg', 12800, 18800, 1, 1),
(1000010, 4, '单人豪华餐', '单人精选 · 主食+饮品', 'https://img.meituan.net/msmerchant/232f8fdf09050838bd33fb24e79f30f9606056.jpg', 6800, 9800, 0, 1),
(1000011, 4, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://img.meituan.net/msmerchant/232f8fdf09050838bd33fb24e79f30f9606056.jpg', 15800, 22800, 0, 1),
(1000012, 5, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://img.meituan.net/msmerchant/054b5de0ba0b50c18a620cc37482129a45739.jpg', 12800, 18800, 1, 1),
(1000013, 5, '单人豪华餐', '单人精选 · 主食+饮品', 'https://img.meituan.net/msmerchant/054b5de0ba0b50c18a620cc37482129a45739.jpg', 6800, 9800, 0, 1),
(1000014, 5, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://img.meituan.net/msmerchant/054b5de0ba0b50c18a620cc37482129a45739.jpg', 15800, 22800, 0, 1),
(1000015, 6, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://img.meituan.net/msmerchant/e71a2d0d693b3033c15522c43e03f09198239.jpg', 12800, 18800, 1, 1),
(1000016, 6, '单人豪华餐', '单人精选 · 主食+饮品', 'https://img.meituan.net/msmerchant/e71a2d0d693b3033c15522c43e03f09198239.jpg', 6800, 9800, 0, 1),
(1000017, 6, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://img.meituan.net/msmerchant/e71a2d0d693b3033c15522c43e03f09198239.jpg', 15800, 22800, 0, 1),
(1000018, 7, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://img.meituan.net/msmerchant/909434939a49b36f340523232924402166854.jpg', 12800, 18800, 1, 1),
(1000019, 7, '单人豪华餐', '单人精选 · 主食+饮品', 'https://img.meituan.net/msmerchant/909434939a49b36f340523232924402166854.jpg', 6800, 9800, 0, 1),
(1000020, 7, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://img.meituan.net/msmerchant/909434939a49b36f340523232924402166854.jpg', 15800, 22800, 0, 1),
(1000021, 8, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://img.meituan.net/msmerchant/cf3dff697bf7f6e11f4b79c4e7d989e4591290.jpg', 12800, 18800, 1, 1),
(1000022, 8, '单人豪华餐', '单人精选 · 主食+饮品', 'https://img.meituan.net/msmerchant/cf3dff697bf7f6e11f4b79c4e7d989e4591290.jpg', 6800, 9800, 0, 1),
(1000023, 8, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://img.meituan.net/msmerchant/cf3dff697bf7f6e11f4b79c4e7d989e4591290.jpg', 15800, 22800, 0, 1),
(1000024, 9, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://p0.meituan.net/biztone/163160492_1624251899456.jpeg', 12800, 18800, 1, 1),
(1000025, 9, '单人豪华餐', '单人精选 · 主食+饮品', 'https://p0.meituan.net/biztone/163160492_1624251899456.jpeg', 6800, 9800, 0, 1),
(1000026, 9, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://p0.meituan.net/biztone/163160492_1624251899456.jpeg', 15800, 22800, 0, 1),
(1000027, 10, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://p0.meituan.net/joymerchant/a575fd4adb0b9099c5c410058148b307-674435191.jpg', 9900, 16800, 1, 1),
(1000028, 10, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://p0.meituan.net/joymerchant/a575fd4adb0b9099c5c410058148b307-674435191.jpg', 13900, 22800, 0, 1),
(1000029, 11, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://p0.meituan.net/dpmerchantpic/53e74b200211d68988a4f02ae9912c6c1076826.jpg', 9900, 16800, 1, 1),
(1000030, 11, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://p0.meituan.net/dpmerchantpic/53e74b200211d68988a4f02ae9912c6c1076826.jpg', 13900, 22800, 0, 1),
(1000031, 12, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://p0.meituan.net/dpmerchantpic/63833f6ba0393e2e8722420ef33f3d40466664.jpg', 9900, 16800, 1, 1),
(1000032, 12, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://p0.meituan.net/dpmerchantpic/63833f6ba0393e2e8722420ef33f3d40466664.jpg', 13900, 22800, 0, 1),
(1000033, 13, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://p1.meituan.net/merchantpic/598c83a8c0d06fe79ca01056e214d345875600.jpg', 9900, 16800, 1, 1),
(1000034, 13, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://p1.meituan.net/merchantpic/598c83a8c0d06fe79ca01056e214d345875600.jpg', 13900, 22800, 0, 1),
(1000035, 14, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://p0.meituan.net/dpmerchantpic/f4cd6d8d4eb1959c3ea826aa05a552c01840451.jpg', 9900, 16800, 1, 1),
(1000036, 14, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://p0.meituan.net/dpmerchantpic/f4cd6d8d4eb1959c3ea826aa05a552c01840451.jpg', 13900, 22800, 0, 1),
(1000037, 15, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'http://store.is.autonavi.com/showpic/ec3440dbf3d8f9de23da5c7a0cf58ad7', 12800, 18800, 1, 1),
(1000038, 15, '单人豪华餐', '单人精选 · 主食+饮品', 'http://store.is.autonavi.com/showpic/ec3440dbf3d8f9de23da5c7a0cf58ad7', 6800, 9800, 0, 1),
(1000039, 15, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'http://store.is.autonavi.com/showpic/ec3440dbf3d8f9de23da5c7a0cf58ad7', 15800, 22800, 0, 1),
(1000040, 16, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://store.is.autonavi.com/showpic/32447f89d8536830bca5ce87abe9e995', 12800, 18800, 1, 1),
(1000041, 16, '单人豪华餐', '单人精选 · 主食+饮品', 'https://store.is.autonavi.com/showpic/32447f89d8536830bca5ce87abe9e995', 6800, 9800, 0, 1),
(1000042, 16, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://store.is.autonavi.com/showpic/32447f89d8536830bca5ce87abe9e995', 15800, 22800, 0, 1),
(1000043, 17, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://store.is.autonavi.com/showpic/f7e8d1baa288de84a152d6d01d7d714e', 12800, 18800, 1, 1),
(1000044, 17, '单人豪华餐', '单人精选 · 主食+饮品', 'https://store.is.autonavi.com/showpic/f7e8d1baa288de84a152d6d01d7d714e', 6800, 9800, 0, 1),
(1000045, 17, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://store.is.autonavi.com/showpic/f7e8d1baa288de84a152d6d01d7d714e', 15800, 22800, 0, 1),
(1000046, 18, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://store.is.autonavi.com/showpic/3727eb8344a3b3d80000000732408607?type=pic', 12800, 18800, 1, 1),
(1000047, 18, '单人豪华餐', '单人精选 · 主食+饮品', 'https://store.is.autonavi.com/showpic/3727eb8344a3b3d80000000732408607?type=pic', 6800, 9800, 0, 1),
(1000048, 18, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://store.is.autonavi.com/showpic/3727eb8344a3b3d80000000732408607?type=pic', 15800, 22800, 0, 1),
(1000049, 19, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://aos-comment.amap.com/B0LDCHT5W1/comment/content_media_external_file_100001891_1763346765703_82580146.jpg', 12800, 18800, 1, 1),
(1000050, 19, '单人豪华餐', '单人精选 · 主食+饮品', 'https://aos-comment.amap.com/B0LDCHT5W1/comment/content_media_external_file_100001891_1763346765703_82580146.jpg', 6800, 9800, 0, 1),
(1000051, 19, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://aos-comment.amap.com/B0LDCHT5W1/comment/content_media_external_file_100001891_1763346765703_82580146.jpg', 15800, 22800, 0, 1),
(1000052, 20, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://aos-comment.amap.com/B0H10YTIAC/comment/176960348868_1769603489850_73131913.jpg', 12800, 18800, 1, 1),
(1000053, 20, '单人豪华餐', '单人精选 · 主食+饮品', 'https://aos-comment.amap.com/B0H10YTIAC/comment/176960348868_1769603489850_73131913.jpg', 6800, 9800, 0, 1),
(1000054, 20, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://aos-comment.amap.com/B0H10YTIAC/comment/176960348868_1769603489850_73131913.jpg', 15800, 22800, 0, 1),
(1000055, 21, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'http://store.is.autonavi.com/query_pic?id=stefd2c728-c3e9-474c-ae07-5934750bf0bc&user=search&operate=original', 12800, 18800, 1, 1),
(1000056, 21, '单人豪华餐', '单人精选 · 主食+饮品', 'http://store.is.autonavi.com/query_pic?id=stefd2c728-c3e9-474c-ae07-5934750bf0bc&user=search&operate=original', 6800, 9800, 0, 1),
(1000057, 21, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'http://store.is.autonavi.com/query_pic?id=stefd2c728-c3e9-474c-ae07-5934750bf0bc&user=search&operate=original', 15800, 22800, 0, 1),
(1000058, 22, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'http://store.is.autonavi.com/showpic/eef4a112ce685ef359071c4a0a1909ea', 12800, 18800, 1, 1),
(1000059, 22, '单人豪华餐', '单人精选 · 主食+饮品', 'http://store.is.autonavi.com/showpic/eef4a112ce685ef359071c4a0a1909ea', 6800, 9800, 0, 1),
(1000060, 22, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'http://store.is.autonavi.com/showpic/eef4a112ce685ef359071c4a0a1909ea', 15800, 22800, 0, 1),
(1000061, 23, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://store.is.autonavi.com/showpic/a902c873ad914c287ab8726d75ba46f1', 12800, 18800, 1, 1),
(1000062, 23, '单人豪华餐', '单人精选 · 主食+饮品', 'https://store.is.autonavi.com/showpic/a902c873ad914c287ab8726d75ba46f1', 6800, 9800, 0, 1),
(1000063, 23, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://store.is.autonavi.com/showpic/a902c873ad914c287ab8726d75ba46f1', 15800, 22800, 0, 1),
(1000064, 24, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://aos-comment.amap.com/B0LDCAN19S/comment/176959561761_1769595622903_01861738.jpg', 12800, 18800, 1, 1),
(1000065, 24, '单人豪华餐', '单人精选 · 主食+饮品', 'https://aos-comment.amap.com/B0LDCAN19S/comment/176959561761_1769595622903_01861738.jpg', 6800, 9800, 0, 1),
(1000066, 24, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://aos-comment.amap.com/B0LDCAN19S/comment/176959561761_1769595622903_01861738.jpg', 15800, 22800, 0, 1),
(1000067, 25, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'http://store.is.autonavi.com/query_pic?id=st1c70706a-ddd6-4231-b85f-91ffd4561d53&user=search&operate=original', 12800, 18800, 1, 1),
(1000068, 25, '单人豪华餐', '单人精选 · 主食+饮品', 'http://store.is.autonavi.com/query_pic?id=st1c70706a-ddd6-4231-b85f-91ffd4561d53&user=search&operate=original', 6800, 9800, 0, 1),
(1000069, 25, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'http://store.is.autonavi.com/query_pic?id=st1c70706a-ddd6-4231-b85f-91ffd4561d53&user=search&operate=original', 15800, 22800, 0, 1),
(1000070, 26, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://store.is.autonavi.com/showpic/05a811167c0d5737d5bdd0c524036089', 12800, 18800, 1, 1),
(1000071, 26, '单人豪华餐', '单人精选 · 主食+饮品', 'https://store.is.autonavi.com/showpic/05a811167c0d5737d5bdd0c524036089', 6800, 9800, 0, 1),
(1000072, 26, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://store.is.autonavi.com/showpic/05a811167c0d5737d5bdd0c524036089', 15800, 22800, 0, 1),
(1000073, 27, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://store.is.autonavi.com/showpic/a95e624e701a87fbf89cede1dc5dd9df', 12800, 18800, 1, 1),
(1000074, 27, '单人豪华餐', '单人精选 · 主食+饮品', 'https://store.is.autonavi.com/showpic/a95e624e701a87fbf89cede1dc5dd9df', 6800, 9800, 0, 1),
(1000075, 27, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://store.is.autonavi.com/showpic/a95e624e701a87fbf89cede1dc5dd9df', 15800, 22800, 0, 1),
(1000076, 28, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://store.is.autonavi.com/showpic/efd3a911c5755edd765b13c0334b9d9d', 12800, 18800, 1, 1),
(1000077, 28, '单人豪华餐', '单人精选 · 主食+饮品', 'https://store.is.autonavi.com/showpic/efd3a911c5755edd765b13c0334b9d9d', 6800, 9800, 0, 1),
(1000078, 28, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://store.is.autonavi.com/showpic/efd3a911c5755edd765b13c0334b9d9d', 15800, 22800, 0, 1),
(1000079, 29, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://store.is.autonavi.com/showpic/71d4b5d167b55dfd96b739f843f02007', 12800, 18800, 1, 1),
(1000080, 29, '单人豪华餐', '单人精选 · 主食+饮品', 'https://store.is.autonavi.com/showpic/71d4b5d167b55dfd96b739f843f02007', 6800, 9800, 0, 1),
(1000081, 29, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://store.is.autonavi.com/showpic/71d4b5d167b55dfd96b739f843f02007', 15800, 22800, 0, 1),
(1000082, 30, '欢唱3小时套餐', '含果盘 · 非节假日可用', '', 9900, 16800, 1, 1),
(1000083, 30, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', '', 13900, 22800, 0, 1),
(1000084, 31, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'http://store.is.autonavi.com/showpic/72fae3c886ab8682a731a2ef8c5b81e4', 9900, 16800, 1, 1),
(1000085, 31, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'http://store.is.autonavi.com/showpic/72fae3c886ab8682a731a2ef8c5b81e4', 13900, 22800, 0, 1),
(1000086, 32, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'http://store.is.autonavi.com/showpic/29d785ac3d55524bf4124cb285c8b679', 9900, 16800, 1, 1),
(1000087, 32, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'http://store.is.autonavi.com/showpic/29d785ac3d55524bf4124cb285c8b679', 13900, 22800, 0, 1),
(1000088, 33, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'http://store.is.autonavi.com/query_pic?id=st77d091b5-b1e2-4e76-be7b-85696a809271&user=search&operate=original', 9900, 16800, 1, 1),
(1000089, 33, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'http://store.is.autonavi.com/query_pic?id=st77d091b5-b1e2-4e76-be7b-85696a809271&user=search&operate=original', 13900, 22800, 0, 1),
(1000090, 34, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://store.is.autonavi.com/showpic/9f4f57e77dd14947cd805ecb6a09e1a0', 9900, 16800, 1, 1),
(1000091, 34, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://store.is.autonavi.com/showpic/9f4f57e77dd14947cd805ecb6a09e1a0', 13900, 22800, 0, 1),
(1000092, 35, '欢唱3小时套餐', '含果盘 · 非节假日可用', '', 9900, 16800, 1, 1),
(1000093, 35, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', '', 13900, 22800, 0, 1),
(1000094, 36, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'http://store.is.autonavi.com/showpic/353305876b6a31993fc1ed859c3d7f53', 9900, 16800, 1, 1),
(1000095, 36, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'http://store.is.autonavi.com/showpic/353305876b6a31993fc1ed859c3d7f53', 13900, 22800, 0, 1),
(1000096, 37, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://aos-comment.amap.com/B0HAL17K9K/comment/content_media_external_file_2378058_ss__1771599399083_82183626.jpg', 9900, 16800, 1, 1),
(1000097, 37, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://aos-comment.amap.com/B0HAL17K9K/comment/content_media_external_file_2378058_ss__1771599399083_82183626.jpg', 13900, 22800, 0, 1),
(1000098, 38, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'http://store.is.autonavi.com/showpic/157f9da8cab185fe276d4e32e662d93d', 9900, 16800, 1, 1),
(1000099, 38, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'http://store.is.autonavi.com/showpic/157f9da8cab185fe276d4e32e662d93d', 13900, 22800, 0, 1),
(1000100, 39, '欢唱3小时套餐', '含果盘 · 非节假日可用', '', 9900, 16800, 1, 1),
(1000101, 39, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', '', 13900, 22800, 0, 1),
(1000102, 40, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://aos-comment.amap.com/comment/content_service_c1612df54dcdb5a16d71ef79543bdb3a_1768614763497_26282537.jpg', 9900, 16800, 1, 1),
(1000103, 40, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://aos-comment.amap.com/comment/content_service_c1612df54dcdb5a16d71ef79543bdb3a_1768614763497_26282537.jpg', 13900, 22800, 0, 1),
(1000104, 41, '欢唱3小时套餐', '含果盘 · 非节假日可用', '', 9900, 16800, 1, 1),
(1000105, 41, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', '', 13900, 22800, 0, 1),
(1000106, 42, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'http://store.is.autonavi.com/showpic/353305876b6a31993fc1ed859c3d7f53', 9900, 16800, 1, 1),
(1000107, 42, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'http://store.is.autonavi.com/showpic/353305876b6a31993fc1ed859c3d7f53', 13900, 22800, 0, 1),
(1000108, 43, '欢唱3小时套餐', '含果盘 · 非节假日可用', '', 9900, 16800, 1, 1),
(1000109, 43, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', '', 13900, 22800, 0, 1),
(1000110, 44, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'http://store.is.autonavi.com/query_pic?id=st0d91a280-74b8-4ee2-b2ba-9b726f40b536&user=search&operate=original', 9900, 16800, 1, 1),
(1000111, 44, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'http://store.is.autonavi.com/query_pic?id=st0d91a280-74b8-4ee2-b2ba-9b726f40b536&user=search&operate=original', 13900, 22800, 0, 1),
(1000112, 45, '洗剪吹套餐', '含洗头+剪发+造型', 'https://store.is.autonavi.com/showpic/da9e7c4cd4a4832054a69313ff59e1e0', 5800, 9800, 1, 1),
(1000113, 45, '烫发+护理套餐', '含洗剪吹 · 需预约', 'https://store.is.autonavi.com/showpic/da9e7c4cd4a4832054a69313ff59e1e0', 16800, 28800, 0, 1),
(1000114, 46, '洗剪吹套餐', '含洗头+剪发+造型', 'https://store.is.autonavi.com/showpic/71a80a23a0013e0eb22116c8b731f8d8', 5800, 9800, 1, 1),
(1000115, 46, '烫发+护理套餐', '含洗剪吹 · 需预约', 'https://store.is.autonavi.com/showpic/71a80a23a0013e0eb22116c8b731f8d8', 16800, 28800, 0, 1),
(1000116, 47, '洗剪吹套餐', '含洗头+剪发+造型', 'https://store.is.autonavi.com/showpic/12567d030e77c3980000002983864284?type=pic', 5800, 9800, 1, 1),
(1000117, 47, '烫发+护理套餐', '含洗剪吹 · 需预约', 'https://store.is.autonavi.com/showpic/12567d030e77c3980000002983864284?type=pic', 16800, 28800, 0, 1),
(1000118, 48, '洗剪吹套餐', '含洗头+剪发+造型', 'https://store.is.autonavi.com/showpic/b3b48cb07f2c4ebb759dcb4aeb137668', 5800, 9800, 1, 1),
(1000119, 48, '烫发+护理套餐', '含洗剪吹 · 需预约', 'https://store.is.autonavi.com/showpic/b3b48cb07f2c4ebb759dcb4aeb137668', 16800, 28800, 0, 1),
(1000120, 49, '洗剪吹套餐', '含洗头+剪发+造型', 'https://store.is.autonavi.com/showpic/166196de4b95b6beec11da1187d75e43', 5800, 9800, 1, 1),
(1000121, 49, '烫发+护理套餐', '含洗剪吹 · 需预约', 'https://store.is.autonavi.com/showpic/166196de4b95b6beec11da1187d75e43', 16800, 28800, 0, 1),
(1000122, 50, '洗剪吹套餐', '含洗头+剪发+造型', 'http://store.is.autonavi.com/showpic/cf579b825499a241718126e9c5ee5786', 5800, 9800, 1, 1),
(1000123, 50, '烫发+护理套餐', '含洗剪吹 · 需预约', 'http://store.is.autonavi.com/showpic/cf579b825499a241718126e9c5ee5786', 16800, 28800, 0, 1),
(1000124, 51, '洗剪吹套餐', '含洗头+剪发+造型', 'http://store.is.autonavi.com/showpic/0a71308b1ce4d117e000555128520226', 5800, 9800, 1, 1),
(1000125, 51, '烫发+护理套餐', '含洗剪吹 · 需预约', 'http://store.is.autonavi.com/showpic/0a71308b1ce4d117e000555128520226', 16800, 28800, 0, 1),
(1000126, 52, '洗剪吹套餐', '含洗头+剪发+造型', 'https://store.is.autonavi.com/showpic/0bb51f0b423bd383cfa2f0d633707f4d', 5800, 9800, 1, 1),
(1000127, 52, '烫发+护理套餐', '含洗剪吹 · 需预约', 'https://store.is.autonavi.com/showpic/0bb51f0b423bd383cfa2f0d633707f4d', 16800, 28800, 0, 1),
(1000128, 53, '洗剪吹套餐', '含洗头+剪发+造型', 'https://store.is.autonavi.com/showpic/1d97cbf5e1dd0ac0a4bfb336dca02a8d', 5800, 9800, 1, 1),
(1000129, 53, '烫发+护理套餐', '含洗剪吹 · 需预约', 'https://store.is.autonavi.com/showpic/1d97cbf5e1dd0ac0a4bfb336dca02a8d', 16800, 28800, 0, 1),
(1000130, 54, '洗剪吹套餐', '含洗头+剪发+造型', 'https://store.is.autonavi.com/showpic/b7b5c19b6473f430ce15f9c28430e16a', 5800, 9800, 1, 1),
(1000131, 54, '烫发+护理套餐', '含洗剪吹 · 需预约', 'https://store.is.autonavi.com/showpic/b7b5c19b6473f430ce15f9c28430e16a', 16800, 28800, 0, 1),
(1000132, 55, '洗剪吹套餐', '含洗头+剪发+造型', 'https://store.is.autonavi.com/showpic/e3960c723598c672b5bf7f4c6b6a4118', 5800, 9800, 1, 1),
(1000133, 55, '烫发+护理套餐', '含洗剪吹 · 需预约', 'https://store.is.autonavi.com/showpic/e3960c723598c672b5bf7f4c6b6a4118', 16800, 28800, 0, 1),
(1000134, 56, '洗剪吹套餐', '含洗头+剪发+造型', 'https://store.is.autonavi.com/showpic/d1a34620072c1c28c58ea963888ec4ba', 5800, 9800, 1, 1),
(1000135, 56, '烫发+护理套餐', '含洗剪吹 · 需预约', 'https://store.is.autonavi.com/showpic/d1a34620072c1c28c58ea963888ec4ba', 16800, 28800, 0, 1),
(1000136, 57, '洗剪吹套餐', '含洗头+剪发+造型', 'http://store.is.autonavi.com/showpic/9bb4f44145a0085590d63c957aac8d9c', 5800, 9800, 1, 1),
(1000137, 57, '烫发+护理套餐', '含洗剪吹 · 需预约', 'http://store.is.autonavi.com/showpic/9bb4f44145a0085590d63c957aac8d9c', 16800, 28800, 0, 1),
(1000138, 58, '洗剪吹套餐', '含洗头+剪发+造型', 'https://store.is.autonavi.com/showpic/3a3c936953ef5f7806546cdccdaefd13', 5800, 9800, 1, 1),
(1000139, 58, '烫发+护理套餐', '含洗剪吹 · 需预约', 'https://store.is.autonavi.com/showpic/3a3c936953ef5f7806546cdccdaefd13', 16800, 28800, 0, 1),
(1000140, 59, '洗剪吹套餐', '含洗头+剪发+造型', 'http://store.is.autonavi.com/showpic/e07eb5523136db6120369254c3c55106', 5800, 9800, 1, 1),
(1000141, 59, '烫发+护理套餐', '含洗剪吹 · 需预约', 'http://store.is.autonavi.com/showpic/e07eb5523136db6120369254c3c55106', 16800, 28800, 0, 1),
(1000142, 60, '单次体验卡', '器械+团课任选一次', 'https://store.is.autonavi.com/showpic/472dc8593c0f06e30000002363008976?type=pic', 2900, 5800, 1, 1),
(1000143, 60, '健身月卡', '30天不限次 · 含团课', 'https://store.is.autonavi.com/showpic/472dc8593c0f06e30000002363008976?type=pic', 19900, 39900, 0, 1),
(1000144, 61, '单次体验卡', '器械+团课任选一次', 'https://store.is.autonavi.com/showpic/faf6ca2602d8082ffc9f9e0617626b82', 2900, 5800, 1, 1),
(1000145, 61, '健身月卡', '30天不限次 · 含团课', 'https://store.is.autonavi.com/showpic/faf6ca2602d8082ffc9f9e0617626b82', 19900, 39900, 0, 1),
(1000146, 62, '单次体验卡', '器械+团课任选一次', 'https://store.is.autonavi.com/showpic/73d3d5442e645e8be54b1896c2614d8b', 2900, 5800, 1, 1),
(1000147, 62, '健身月卡', '30天不限次 · 含团课', 'https://store.is.autonavi.com/showpic/73d3d5442e645e8be54b1896c2614d8b', 19900, 39900, 0, 1),
(1000148, 63, '单次体验卡', '器械+团课任选一次', 'https://store.is.autonavi.com/showpic/ec4db50315bbe23a586610a469790886', 2900, 5800, 1, 1),
(1000149, 63, '健身月卡', '30天不限次 · 含团课', 'https://store.is.autonavi.com/showpic/ec4db50315bbe23a586610a469790886', 19900, 39900, 0, 1),
(1000150, 64, '单次体验卡', '器械+团课任选一次', 'http://store.is.autonavi.com/showpic/b8bee1b5a59310bcca48f1fa227bad11', 2900, 5800, 1, 1),
(1000151, 64, '健身月卡', '30天不限次 · 含团课', 'http://store.is.autonavi.com/showpic/b8bee1b5a59310bcca48f1fa227bad11', 19900, 39900, 0, 1),
(1000152, 65, '单次体验卡', '器械+团课任选一次', 'https://aos-comment.amap.com/B0M6SR501P/comment/307B33A7_0D74_4999_896A_91F7767EFCDF_L0_001_2000_112_1769080851068_34955369.jpg', 2900, 5800, 1, 1),
(1000153, 65, '健身月卡', '30天不限次 · 含团课', 'https://aos-comment.amap.com/B0M6SR501P/comment/307B33A7_0D74_4999_896A_91F7767EFCDF_L0_001_2000_112_1769080851068_34955369.jpg', 19900, 39900, 0, 1),
(1000154, 66, '单次体验卡', '器械+团课任选一次', '', 2900, 5800, 1, 1),
(1000155, 66, '健身月卡', '30天不限次 · 含团课', '', 19900, 39900, 0, 1),
(1000156, 67, '单次体验卡', '器械+团课任选一次', 'https://aos-comment.amap.com/B0JKLK0A08/comment/41FB7B08_C71E_43EC_A302_BA7BE8327AE3_L0_001_2000_112_1773584576309_42224749.jpg', 2900, 5800, 1, 1),
(1000157, 67, '健身月卡', '30天不限次 · 含团课', 'https://aos-comment.amap.com/B0JKLK0A08/comment/41FB7B08_C71E_43EC_A302_BA7BE8327AE3_L0_001_2000_112_1773584576309_42224749.jpg', 19900, 39900, 0, 1),
(1000158, 68, '单次体验卡', '器械+团课任选一次', 'http://store.is.autonavi.com/showpic/90ae9da0eda62c65ce82c0b61b240bc6', 2900, 5800, 1, 1),
(1000159, 68, '健身月卡', '30天不限次 · 含团课', 'http://store.is.autonavi.com/showpic/90ae9da0eda62c65ce82c0b61b240bc6', 19900, 39900, 0, 1),
(1000160, 69, '单次体验卡', '器械+团课任选一次', 'https://store.is.autonavi.com/showpic/f2dad82559a7526ff01c63bd99918b0b', 2900, 5800, 1, 1),
(1000161, 69, '健身月卡', '30天不限次 · 含团课', 'https://store.is.autonavi.com/showpic/f2dad82559a7526ff01c63bd99918b0b', 19900, 39900, 0, 1),
(1000162, 70, '单次体验卡', '器械+团课任选一次', 'https://store.is.autonavi.com/showpic/62d629580ce235be5a297b29acf75b6d', 2900, 5800, 1, 1),
(1000163, 70, '健身月卡', '30天不限次 · 含团课', 'https://store.is.autonavi.com/showpic/62d629580ce235be5a297b29acf75b6d', 19900, 39900, 0, 1),
(1000164, 71, '单次体验卡', '器械+团课任选一次', 'https://store.is.autonavi.com/showpic/f83f93a83aa2a6ad2ef9517f4ea25c1e', 2900, 5800, 1, 1),
(1000165, 71, '健身月卡', '30天不限次 · 含团课', 'https://store.is.autonavi.com/showpic/f83f93a83aa2a6ad2ef9517f4ea25c1e', 19900, 39900, 0, 1),
(1000166, 72, '单次体验卡', '器械+团课任选一次', 'https://store.is.autonavi.com/showpic/7973c16d0870ab8a1b2ec92dc2e7c15f', 2900, 5800, 1, 1),
(1000167, 72, '健身月卡', '30天不限次 · 含团课', 'https://store.is.autonavi.com/showpic/7973c16d0870ab8a1b2ec92dc2e7c15f', 19900, 39900, 0, 1),
(1000168, 73, '单次体验卡', '器械+团课任选一次', 'https://aos-comment.amap.com/B0JAR9W3G6/comment/91F6AFB9_BA80_4DC0_B267_5AA1EC93E183_L0_001_1500_200_1770878482665_54626751.jpg', 2900, 5800, 1, 1),
(1000169, 73, '健身月卡', '30天不限次 · 含团课', 'https://aos-comment.amap.com/B0JAR9W3G6/comment/91F6AFB9_BA80_4DC0_B267_5AA1EC93E183_L0_001_1500_200_1770878482665_54626751.jpg', 19900, 39900, 0, 1),
(1000170, 74, '单次体验卡', '器械+团课任选一次', '', 2900, 5800, 1, 1),
(1000171, 74, '健身月卡', '30天不限次 · 含团课', '', 19900, 39900, 0, 1),
(1000172, 75, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/656d361522a62dc2c4c9180d423002fa', 6900, 12800, 1, 1),
(1000173, 75, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/656d361522a62dc2c4c9180d423002fa', 12800, 22800, 0, 1),
(1000174, 76, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/672fc1dd206329bada5367714ca2bc0d', 6900, 12800, 1, 1),
(1000175, 76, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/672fc1dd206329bada5367714ca2bc0d', 12800, 22800, 0, 1),
(1000176, 77, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/66023e4561d53965d0b9f6803b7786ec', 6900, 12800, 1, 1),
(1000177, 77, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/66023e4561d53965d0b9f6803b7786ec', 12800, 22800, 0, 1),
(1000178, 78, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/fc5bdb7b74946c56eb4ebe6bb3b0ffab', 6900, 12800, 1, 1),
(1000179, 78, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/fc5bdb7b74946c56eb4ebe6bb3b0ffab', 12800, 22800, 0, 1),
(1000180, 79, '足疗60分钟', '传统足疗 · 赠茶点', 'http://store.is.autonavi.com/showpic/398757ef8cc6c16217a766b375bdbf22', 6900, 12800, 1, 1),
(1000181, 79, '全身按摩套餐', '90分钟全身SPA按摩', 'http://store.is.autonavi.com/showpic/398757ef8cc6c16217a766b375bdbf22', 12800, 22800, 0, 1),
(1000182, 80, '足疗60分钟', '传统足疗 · 赠茶点', 'https://aos-comment.amap.com/B0JA5ABJZT/headerImg/f8520a6281dc21df5b1faf0d3e36f9f5_2048_2048_80.jpg', 6900, 12800, 1, 1),
(1000183, 80, '全身按摩套餐', '90分钟全身SPA按摩', 'https://aos-comment.amap.com/B0JA5ABJZT/headerImg/f8520a6281dc21df5b1faf0d3e36f9f5_2048_2048_80.jpg', 12800, 22800, 0, 1),
(1000184, 81, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/e853eb0ce078db15dd7e51ab04df3d20', 6900, 12800, 1, 1),
(1000185, 81, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/e853eb0ce078db15dd7e51ab04df3d20', 12800, 22800, 0, 1),
(1000186, 82, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/7fcd6cc8be433dba6d90efa5118715a1', 6900, 12800, 1, 1),
(1000187, 82, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/7fcd6cc8be433dba6d90efa5118715a1', 12800, 22800, 0, 1),
(1000188, 83, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/9aec931e29d293656e8e73d208d32a22', 6900, 12800, 1, 1),
(1000189, 83, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/9aec931e29d293656e8e73d208d32a22', 12800, 22800, 0, 1),
(1000190, 84, '足疗60分钟', '传统足疗 · 赠茶点', '', 6900, 12800, 1, 1),
(1000191, 84, '全身按摩套餐', '90分钟全身SPA按摩', '', 12800, 22800, 0, 1),
(1000192, 85, '足疗60分钟', '传统足疗 · 赠茶点', 'https://aos-comment.amap.com/B0G1DMDNGQ/comment/content_media_external_file_100000047_1753269600153_80396571.jpg', 6900, 12800, 1, 1),
(1000193, 85, '全身按摩套餐', '90分钟全身SPA按摩', 'https://aos-comment.amap.com/B0G1DMDNGQ/comment/content_media_external_file_100000047_1753269600153_80396571.jpg', 12800, 22800, 0, 1),
(1000194, 86, '足疗60分钟', '传统足疗 · 赠茶点', 'http://store.is.autonavi.com/showpic/83814101b27d16210b7d1947a5c9d7d6', 6900, 12800, 1, 1),
(1000195, 86, '全身按摩套餐', '90分钟全身SPA按摩', 'http://store.is.autonavi.com/showpic/83814101b27d16210b7d1947a5c9d7d6', 12800, 22800, 0, 1),
(1000196, 87, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/279118f0e37b3eb8490d8fe74b38c195', 6900, 12800, 1, 1),
(1000197, 87, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/279118f0e37b3eb8490d8fe74b38c195', 12800, 22800, 0, 1),
(1000198, 88, '足疗60分钟', '传统足疗 · 赠茶点', 'http://store.is.autonavi.com/showpic/fd95a8af3eecc54cae83286ad165ff30', 6900, 12800, 1, 1),
(1000199, 88, '全身按摩套餐', '90分钟全身SPA按摩', 'http://store.is.autonavi.com/showpic/fd95a8af3eecc54cae83286ad165ff30', 12800, 22800, 0, 1),
(1000200, 89, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/9c1f85cabb6aab24026691a1089cca18', 6900, 12800, 1, 1),
(1000201, 89, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/9c1f85cabb6aab24026691a1089cca18', 12800, 22800, 0, 1),
(1000202, 90, '面部深层护理', '清洁+补水+按摩', 'https://store.is.autonavi.com/showpic/da9e7c4cd4a4832054a69313ff59e1e0', 15900, 28800, 1, 1),
(1000203, 90, '全身SPA套餐', '120分钟全身放松', 'https://store.is.autonavi.com/showpic/da9e7c4cd4a4832054a69313ff59e1e0', 25900, 42800, 0, 1),
(1000204, 91, '面部深层护理', '清洁+补水+按摩', 'https://store.is.autonavi.com/showpic/71a80a23a0013e0eb22116c8b731f8d8', 15900, 28800, 1, 1),
(1000205, 91, '全身SPA套餐', '120分钟全身放松', 'https://store.is.autonavi.com/showpic/71a80a23a0013e0eb22116c8b731f8d8', 25900, 42800, 0, 1),
(1000206, 92, '面部深层护理', '清洁+补水+按摩', 'https://store.is.autonavi.com/showpic/672fc1dd206329bada5367714ca2bc0d', 15900, 28800, 1, 1),
(1000207, 92, '全身SPA套餐', '120分钟全身放松', 'https://store.is.autonavi.com/showpic/672fc1dd206329bada5367714ca2bc0d', 25900, 42800, 0, 1),
(1000208, 93, '面部深层护理', '清洁+补水+按摩', 'http://store.is.autonavi.com/showpic/311919e07f30a60223cb966957a3d8aa', 15900, 28800, 1, 1),
(1000209, 93, '全身SPA套餐', '120分钟全身放松', 'http://store.is.autonavi.com/showpic/311919e07f30a60223cb966957a3d8aa', 25900, 42800, 0, 1),
(1000210, 94, '面部深层护理', '清洁+补水+按摩', 'https://store.is.autonavi.com/showpic/a6242465fc5459420055fc8307212470', 15900, 28800, 1, 1),
(1000211, 94, '全身SPA套餐', '120分钟全身放松', 'https://store.is.autonavi.com/showpic/a6242465fc5459420055fc8307212470', 25900, 42800, 0, 1),
(1000212, 95, '面部深层护理', '清洁+补水+按摩', '', 15900, 28800, 1, 1),
(1000213, 95, '全身SPA套餐', '120分钟全身放松', '', 25900, 42800, 0, 1),
(1000214, 96, '面部深层护理', '清洁+补水+按摩', 'https://store.is.autonavi.com/showpic/2f05682db93572fbbddac5cce7a39226', 15900, 28800, 1, 1),
(1000215, 96, '全身SPA套餐', '120分钟全身放松', 'https://store.is.autonavi.com/showpic/2f05682db93572fbbddac5cce7a39226', 25900, 42800, 0, 1),
(1000216, 97, '面部深层护理', '清洁+补水+按摩', 'https://store.is.autonavi.com/showpic/494eb4a55bc1113c0000000962272253?type=pic', 15900, 28800, 1, 1),
(1000217, 97, '全身SPA套餐', '120分钟全身放松', 'https://store.is.autonavi.com/showpic/494eb4a55bc1113c0000000962272253?type=pic', 25900, 42800, 0, 1),
(1000218, 98, '面部深层护理', '清洁+补水+按摩', 'https://store.is.autonavi.com/showpic/f751459cbb1196d36b386598da5a29cf', 15900, 28800, 1, 1),
(1000219, 98, '全身SPA套餐', '120分钟全身放松', 'https://store.is.autonavi.com/showpic/f751459cbb1196d36b386598da5a29cf', 25900, 42800, 0, 1),
(1000220, 99, '面部深层护理', '清洁+补水+按摩', 'https://store.is.autonavi.com/showpic/87970f3cdf377fe4b96aad7c82c62800', 15900, 28800, 1, 1),
(1000221, 99, '全身SPA套餐', '120分钟全身放松', 'https://store.is.autonavi.com/showpic/87970f3cdf377fe4b96aad7c82c62800', 25900, 42800, 0, 1),
(1000222, 100, '面部深层护理', '清洁+补水+按摩', 'https://store.is.autonavi.com/showpic/821071ee951a33ce0000003048776592?type=pic', 15900, 28800, 1, 1),
(1000223, 100, '全身SPA套餐', '120分钟全身放松', 'https://store.is.autonavi.com/showpic/821071ee951a33ce0000003048776592?type=pic', 25900, 42800, 0, 1),
(1000224, 101, '面部深层护理', '清洁+补水+按摩', 'http://store.is.autonavi.com/showpic/df8cf90a50a3422848cc266e2c27e4a7', 15900, 28800, 1, 1),
(1000225, 101, '全身SPA套餐', '120分钟全身放松', 'http://store.is.autonavi.com/showpic/df8cf90a50a3422848cc266e2c27e4a7', 25900, 42800, 0, 1),
(1000226, 102, '面部深层护理', '清洁+补水+按摩', 'https://store.is.autonavi.com/showpic/ef1528a545841b002de39284c699e85a', 15900, 28800, 1, 1),
(1000227, 102, '全身SPA套餐', '120分钟全身放松', 'https://store.is.autonavi.com/showpic/ef1528a545841b002de39284c699e85a', 25900, 42800, 0, 1),
(1000228, 103, '面部深层护理', '清洁+补水+按摩', 'https://store.is.autonavi.com/showpic/d1e32915786951677be5c70cf0eeec7a', 15900, 28800, 1, 1),
(1000229, 103, '全身SPA套餐', '120分钟全身放松', 'https://store.is.autonavi.com/showpic/d1e32915786951677be5c70cf0eeec7a', 25900, 42800, 0, 1),
(1000230, 104, '面部深层护理', '清洁+补水+按摩', 'https://store.is.autonavi.com/showpic/bf9d3192e4f6e542fa61f45f0402c4fc', 15900, 28800, 1, 1),
(1000231, 104, '全身SPA套餐', '120分钟全身放松', 'https://store.is.autonavi.com/showpic/bf9d3192e4f6e542fa61f45f0402c4fc', 25900, 42800, 0, 1),
(1000232, 105, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'http://store.is.autonavi.com/showpic/7d605bdbe452c6ab12f1feef6333079a', 5900, 9900, 1, 1),
(1000233, 105, '亲子全天通票', '全天不限时 · 含家长陪同', 'http://store.is.autonavi.com/showpic/7d605bdbe452c6ab12f1feef6333079a', 9900, 16800, 0, 1),
(1000234, 106, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'http://store.is.autonavi.com/showpic/34693f47fa172341dd0e0232ddb4dbd9', 5900, 9900, 1, 1),
(1000235, 106, '亲子全天通票', '全天不限时 · 含家长陪同', 'http://store.is.autonavi.com/showpic/34693f47fa172341dd0e0232ddb4dbd9', 9900, 16800, 0, 1),
(1000236, 107, '亲子畅玩2小时', '海洋球+滑梯+沙池', '', 5900, 9900, 1, 1),
(1000237, 107, '亲子全天通票', '全天不限时 · 含家长陪同', '', 9900, 16800, 0, 1),
(1000238, 108, '亲子畅玩2小时', '海洋球+滑梯+沙池', '', 5900, 9900, 1, 1),
(1000239, 108, '亲子全天通票', '全天不限时 · 含家长陪同', '', 9900, 16800, 0, 1),
(1000240, 109, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'https://store.is.autonavi.com/showpic/873028a89848b5f637ef8456625acf32', 5900, 9900, 1, 1),
(1000241, 109, '亲子全天通票', '全天不限时 · 含家长陪同', 'https://store.is.autonavi.com/showpic/873028a89848b5f637ef8456625acf32', 9900, 16800, 0, 1),
(1000242, 110, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'http://store.is.autonavi.com/showpic/b23545c5172436a67e4ba146725e2e3f', 5900, 9900, 1, 1),
(1000243, 110, '亲子全天通票', '全天不限时 · 含家长陪同', 'http://store.is.autonavi.com/showpic/b23545c5172436a67e4ba146725e2e3f', 9900, 16800, 0, 1),
(1000244, 111, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'https://store.is.autonavi.com/showpic/39e6e2ccc13f439fa6e802250d8cba82', 5900, 9900, 1, 1),
(1000245, 111, '亲子全天通票', '全天不限时 · 含家长陪同', 'https://store.is.autonavi.com/showpic/39e6e2ccc13f439fa6e802250d8cba82', 9900, 16800, 0, 1),
(1000246, 112, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'https://store.is.autonavi.com/showpic/5274fc6c1ce7312f94cff1deeb4807ca', 5900, 9900, 1, 1),
(1000247, 112, '亲子全天通票', '全天不限时 · 含家长陪同', 'https://store.is.autonavi.com/showpic/5274fc6c1ce7312f94cff1deeb4807ca', 9900, 16800, 0, 1),
(1000248, 113, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'http://store.is.autonavi.com/showpic/cb0adfe643063342e85f4f3ed5786b21', 5900, 9900, 1, 1),
(1000249, 113, '亲子全天通票', '全天不限时 · 含家长陪同', 'http://store.is.autonavi.com/showpic/cb0adfe643063342e85f4f3ed5786b21', 9900, 16800, 0, 1),
(1000250, 114, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'http://store.is.autonavi.com/showpic/1da338caf575cf19c4cd6738e35c9336', 5900, 9900, 1, 1),
(1000251, 114, '亲子全天通票', '全天不限时 · 含家长陪同', 'http://store.is.autonavi.com/showpic/1da338caf575cf19c4cd6738e35c9336', 9900, 16800, 0, 1),
(1000252, 115, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'https://aos-comment.amap.com/B0FFMBTPZD/comment/13f27050120e6f31dee8ad5027c5ec59_2048_2048_80.jpg', 5900, 9900, 1, 1),
(1000253, 115, '亲子全天通票', '全天不限时 · 含家长陪同', 'https://aos-comment.amap.com/B0FFMBTPZD/comment/13f27050120e6f31dee8ad5027c5ec59_2048_2048_80.jpg', 9900, 16800, 0, 1),
(1000254, 116, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'https://store.is.autonavi.com/showpic/25e0f019f9023a3943a3ddfb7014b5e9', 5900, 9900, 1, 1),
(1000255, 116, '亲子全天通票', '全天不限时 · 含家长陪同', 'https://store.is.autonavi.com/showpic/25e0f019f9023a3943a3ddfb7014b5e9', 9900, 16800, 0, 1),
(1000256, 117, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'https://store.is.autonavi.com/showpic/41782dc27124debb0000003118002515', 5900, 9900, 1, 1),
(1000257, 117, '亲子全天通票', '全天不限时 · 含家长陪同', 'https://store.is.autonavi.com/showpic/41782dc27124debb0000003118002515', 9900, 16800, 0, 1),
(1000258, 118, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'https://store.is.autonavi.com/showpic/b805cc6f481e6a55561eea504427b444', 5900, 9900, 1, 1),
(1000259, 118, '亲子全天通票', '全天不限时 · 含家长陪同', 'https://store.is.autonavi.com/showpic/b805cc6f481e6a55561eea504427b444', 9900, 16800, 0, 1),
(1000260, 119, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'https://aos-comment.amap.com/B023B02CY6/comment/809fb6a285964cae3d578b4275d80421_2048_2048_80.jpg', 5900, 9900, 1, 1),
(1000261, 119, '亲子全天通票', '全天不限时 · 含家长陪同', 'https://aos-comment.amap.com/B023B02CY6/comment/809fb6a285964cae3d578b4275d80421_2048_2048_80.jpg', 9900, 16800, 0, 1),
(1000262, 120, '精酿畅饮套餐', '精酿4杯 · 赠小食', '', 8900, 15800, 1, 1),
(1000263, 120, '双人微醺套餐', '鸡尾酒2杯+果盘', '', 12900, 20800, 0, 1),
(1000264, 121, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'http://store.is.autonavi.com/showpic/39689f5e67ff591f11bb92334fecf020', 8900, 15800, 1, 1),
(1000265, 121, '双人微醺套餐', '鸡尾酒2杯+果盘', 'http://store.is.autonavi.com/showpic/39689f5e67ff591f11bb92334fecf020', 12900, 20800, 0, 1),
(1000266, 122, '精酿畅饮套餐', '精酿4杯 · 赠小食', '', 8900, 15800, 1, 1),
(1000267, 122, '双人微醺套餐', '鸡尾酒2杯+果盘', '', 12900, 20800, 0, 1),
(1000268, 123, '精酿畅饮套餐', '精酿4杯 · 赠小食', '', 8900, 15800, 1, 1),
(1000269, 123, '双人微醺套餐', '鸡尾酒2杯+果盘', '', 12900, 20800, 0, 1),
(1000270, 124, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'http://store.is.autonavi.com/showpic/1e0c4e425dfdc176d585b4ce56dc681c', 8900, 15800, 1, 1),
(1000271, 124, '双人微醺套餐', '鸡尾酒2杯+果盘', 'http://store.is.autonavi.com/showpic/1e0c4e425dfdc176d585b4ce56dc681c', 12900, 20800, 0, 1),
(1000272, 125, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://store.is.autonavi.com/showpic/fad79a0583f88c4d0000002963940396?type=pic', 8900, 15800, 1, 1),
(1000273, 125, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://store.is.autonavi.com/showpic/fad79a0583f88c4d0000002963940396?type=pic', 12900, 20800, 0, 1),
(1000274, 126, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://store.is.autonavi.com/showpic/0d1fc78dd1b67fc8e9a973cce18170cc', 8900, 15800, 1, 1),
(1000275, 126, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://store.is.autonavi.com/showpic/0d1fc78dd1b67fc8e9a973cce18170cc', 12900, 20800, 0, 1),
(1000276, 127, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://store.is.autonavi.com/showpic/76c2642d7a086e4c0390484d32c0dfb0', 8900, 15800, 1, 1),
(1000277, 127, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://store.is.autonavi.com/showpic/76c2642d7a086e4c0390484d32c0dfb0', 12900, 20800, 0, 1),
(1000278, 128, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://aos-comment.amap.com/B0HRUDFXZ1/comment/c3d6dcd0fd9595d9fbb722e4f209f595_2048_2048_80.jpg', 8900, 15800, 1, 1),
(1000279, 128, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://aos-comment.amap.com/B0HRUDFXZ1/comment/c3d6dcd0fd9595d9fbb722e4f209f595_2048_2048_80.jpg', 12900, 20800, 0, 1),
(1000280, 129, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://store.is.autonavi.com/showpic/ecf5d16b898e3d984ec38abbf3054ae0', 8900, 15800, 1, 1),
(1000281, 129, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://store.is.autonavi.com/showpic/ecf5d16b898e3d984ec38abbf3054ae0', 12900, 20800, 0, 1),
(1000282, 130, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://aos-comment.amap.com/B0G3FCLQ41/headerImg/6f21fcbf5030aa215ec9e66e72759282_2048_2048_80.jpg', 8900, 15800, 1, 1),
(1000283, 130, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://aos-comment.amap.com/B0G3FCLQ41/headerImg/6f21fcbf5030aa215ec9e66e72759282_2048_2048_80.jpg', 12900, 20800, 0, 1),
(1000284, 131, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'http://store.is.autonavi.com/showpic/73bb773dfbbccc674d41fd671d5e8049', 8900, 15800, 1, 1),
(1000285, 131, '双人微醺套餐', '鸡尾酒2杯+果盘', 'http://store.is.autonavi.com/showpic/73bb773dfbbccc674d41fd671d5e8049', 12900, 20800, 0, 1),
(1000286, 132, '精酿畅饮套餐', '精酿4杯 · 赠小食', '', 8900, 15800, 1, 1),
(1000287, 132, '双人微醺套餐', '鸡尾酒2杯+果盘', '', 12900, 20800, 0, 1),
(1000288, 133, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://store.is.autonavi.com/showpic/57432fb977c7d4220000003220350339?type=pic', 8900, 15800, 1, 1),
(1000289, 133, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://store.is.autonavi.com/showpic/57432fb977c7d4220000003220350339?type=pic', 12900, 20800, 0, 1),
(1000290, 134, '精酿畅饮套餐', '精酿4杯 · 赠小食', '', 8900, 15800, 1, 1),
(1000291, 134, '双人微醺套餐', '鸡尾酒2杯+果盘', '', 12900, 20800, 0, 1),
(1000292, 135, '包场4小时', '含KTV+桌游 · 最多12人', 'http://store.is.autonavi.com/showpic/7d605bdbe452c6ab12f1feef6333079a', 29900, 49900, 1, 1),
(1000293, 135, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'http://store.is.autonavi.com/showpic/7d605bdbe452c6ab12f1feef6333079a', 49900, 79900, 0, 1),
(1000294, 136, '包场4小时', '含KTV+桌游 · 最多12人', 'http://store.is.autonavi.com/showpic/34693f47fa172341dd0e0232ddb4dbd9', 29900, 49900, 1, 1),
(1000295, 136, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'http://store.is.autonavi.com/showpic/34693f47fa172341dd0e0232ddb4dbd9', 49900, 79900, 0, 1),
(1000296, 137, '包场4小时', '含KTV+桌游 · 最多12人', '', 29900, 49900, 1, 1),
(1000297, 137, '包场8小时豪华套餐', '含KTV+台球+餐饮', '', 49900, 79900, 0, 1),
(1000298, 138, '包场4小时', '含KTV+桌游 · 最多12人', '', 29900, 49900, 1, 1),
(1000299, 138, '包场8小时豪华套餐', '含KTV+台球+餐饮', '', 49900, 79900, 0, 1),
(1000300, 139, '包场4小时', '含KTV+桌游 · 最多12人', 'https://store.is.autonavi.com/showpic/873028a89848b5f637ef8456625acf32', 29900, 49900, 1, 1),
(1000301, 139, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'https://store.is.autonavi.com/showpic/873028a89848b5f637ef8456625acf32', 49900, 79900, 0, 1),
(1000302, 140, '包场4小时', '含KTV+桌游 · 最多12人', 'http://store.is.autonavi.com/showpic/b23545c5172436a67e4ba146725e2e3f', 29900, 49900, 1, 1),
(1000303, 140, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'http://store.is.autonavi.com/showpic/b23545c5172436a67e4ba146725e2e3f', 49900, 79900, 0, 1),
(1000304, 141, '包场4小时', '含KTV+桌游 · 最多12人', 'https://store.is.autonavi.com/showpic/39e6e2ccc13f439fa6e802250d8cba82', 29900, 49900, 1, 1),
(1000305, 141, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'https://store.is.autonavi.com/showpic/39e6e2ccc13f439fa6e802250d8cba82', 49900, 79900, 0, 1),
(1000306, 142, '包场4小时', '含KTV+桌游 · 最多12人', 'https://store.is.autonavi.com/showpic/5274fc6c1ce7312f94cff1deeb4807ca', 29900, 49900, 1, 1),
(1000307, 142, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'https://store.is.autonavi.com/showpic/5274fc6c1ce7312f94cff1deeb4807ca', 49900, 79900, 0, 1),
(1000308, 143, '包场4小时', '含KTV+桌游 · 最多12人', 'http://store.is.autonavi.com/showpic/cb0adfe643063342e85f4f3ed5786b21', 29900, 49900, 1, 1),
(1000309, 143, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'http://store.is.autonavi.com/showpic/cb0adfe643063342e85f4f3ed5786b21', 49900, 79900, 0, 1),
(1000310, 144, '包场4小时', '含KTV+桌游 · 最多12人', 'http://store.is.autonavi.com/showpic/1da338caf575cf19c4cd6738e35c9336', 29900, 49900, 1, 1),
(1000311, 144, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'http://store.is.autonavi.com/showpic/1da338caf575cf19c4cd6738e35c9336', 49900, 79900, 0, 1),
(1000312, 145, '包场4小时', '含KTV+桌游 · 最多12人', 'https://aos-comment.amap.com/B0FFMBTPZD/comment/13f27050120e6f31dee8ad5027c5ec59_2048_2048_80.jpg', 29900, 49900, 1, 1),
(1000313, 145, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'https://aos-comment.amap.com/B0FFMBTPZD/comment/13f27050120e6f31dee8ad5027c5ec59_2048_2048_80.jpg', 49900, 79900, 0, 1),
(1000314, 146, '包场4小时', '含KTV+桌游 · 最多12人', 'https://store.is.autonavi.com/showpic/25e0f019f9023a3943a3ddfb7014b5e9', 29900, 49900, 1, 1),
(1000315, 146, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'https://store.is.autonavi.com/showpic/25e0f019f9023a3943a3ddfb7014b5e9', 49900, 79900, 0, 1),
(1000316, 147, '包场4小时', '含KTV+桌游 · 最多12人', 'https://store.is.autonavi.com/showpic/41782dc27124debb0000003118002515', 29900, 49900, 1, 1),
(1000317, 147, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'https://store.is.autonavi.com/showpic/41782dc27124debb0000003118002515', 49900, 79900, 0, 1),
(1000318, 148, '包场4小时', '含KTV+桌游 · 最多12人', 'https://store.is.autonavi.com/showpic/b805cc6f481e6a55561eea504427b444', 29900, 49900, 1, 1),
(1000319, 148, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'https://store.is.autonavi.com/showpic/b805cc6f481e6a55561eea504427b444', 49900, 79900, 0, 1),
(1000320, 149, '包场4小时', '含KTV+桌游 · 最多12人', 'https://aos-comment.amap.com/B023B02CY6/comment/809fb6a285964cae3d578b4275d80421_2048_2048_80.jpg', 29900, 49900, 1, 1),
(1000321, 149, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'https://aos-comment.amap.com/B023B02CY6/comment/809fb6a285964cae3d578b4275d80421_2048_2048_80.jpg', 49900, 79900, 0, 1),
(1000322, 150, '基础单色美甲', '任选单色 · 含护理', 'https://store.is.autonavi.com/showpic/da9e7c4cd4a4832054a69313ff59e1e0', 3900, 7800, 1, 1),
(1000323, 150, '美甲+美睫套餐', '单色美甲+自然款美睫', 'https://store.is.autonavi.com/showpic/da9e7c4cd4a4832054a69313ff59e1e0', 12900, 21800, 0, 1),
(1000324, 151, '基础单色美甲', '任选单色 · 含护理', 'https://store.is.autonavi.com/showpic/71a80a23a0013e0eb22116c8b731f8d8', 3900, 7800, 1, 1),
(1000325, 151, '美甲+美睫套餐', '单色美甲+自然款美睫', 'https://store.is.autonavi.com/showpic/71a80a23a0013e0eb22116c8b731f8d8', 12900, 21800, 0, 1),
(1000326, 152, '基础单色美甲', '任选单色 · 含护理', '', 3900, 7800, 1, 1),
(1000327, 152, '美甲+美睫套餐', '单色美甲+自然款美睫', '', 12900, 21800, 0, 1),
(1000328, 153, '基础单色美甲', '任选单色 · 含护理', 'http://store.is.autonavi.com/showpic/9da59aa28905cf8fff55f4c85bc84652', 3900, 7800, 1, 1),
(1000329, 153, '美甲+美睫套餐', '单色美甲+自然款美睫', 'http://store.is.autonavi.com/showpic/9da59aa28905cf8fff55f4c85bc84652', 12900, 21800, 0, 1),
(1000330, 154, '基础单色美甲', '任选单色 · 含护理', 'http://store.is.autonavi.com/showpic/2e77f789ee4fae462622d133bb05b21b', 3900, 7800, 1, 1),
(1000331, 154, '美甲+美睫套餐', '单色美甲+自然款美睫', 'http://store.is.autonavi.com/showpic/2e77f789ee4fae462622d133bb05b21b', 12900, 21800, 0, 1),
(1000332, 155, '基础单色美甲', '任选单色 · 含护理', '', 3900, 7800, 1, 1),
(1000333, 155, '美甲+美睫套餐', '单色美甲+自然款美睫', '', 12900, 21800, 0, 1),
(1000334, 156, '基础单色美甲', '任选单色 · 含护理', 'https://aos-comment.amap.com/B0LUHHCMBL/comment/content_media_external_file_1000027544_ss__1758155824355_00167113.jpg', 3900, 7800, 1, 1),
(1000335, 156, '美甲+美睫套餐', '单色美甲+自然款美睫', 'https://aos-comment.amap.com/B0LUHHCMBL/comment/content_media_external_file_1000027544_ss__1758155824355_00167113.jpg', 12900, 21800, 0, 1),
(1000336, 157, '基础单色美甲', '任选单色 · 含护理', 'http://store.is.autonavi.com/showpic/ae4fa0777791a8ed0245661c64b4f619', 3900, 7800, 1, 1),
(1000337, 157, '美甲+美睫套餐', '单色美甲+自然款美睫', 'http://store.is.autonavi.com/showpic/ae4fa0777791a8ed0245661c64b4f619', 12900, 21800, 0, 1),
(1000338, 158, '基础单色美甲', '任选单色 · 含护理', 'https://img.alicdn.com/imgextra/i1/O1CN01rfkOlc1Xq91C6Bmyk_!!6000000002974-0-tps-1080-1080.jpg', 3900, 7800, 1, 1),
(1000339, 158, '美甲+美睫套餐', '单色美甲+自然款美睫', 'https://img.alicdn.com/imgextra/i1/O1CN01rfkOlc1Xq91C6Bmyk_!!6000000002974-0-tps-1080-1080.jpg', 12900, 21800, 0, 1),
(1000340, 159, '基础单色美甲', '任选单色 · 含护理', '', 3900, 7800, 1, 1),
(1000341, 159, '美甲+美睫套餐', '单色美甲+自然款美睫', '', 12900, 21800, 0, 1),
(1000342, 160, '基础单色美甲', '任选单色 · 含护理', 'http://store.is.autonavi.com/showpic/1ecb2ff77658d510ed433c5505a3dc25', 3900, 7800, 1, 1),
(1000343, 160, '美甲+美睫套餐', '单色美甲+自然款美睫', 'http://store.is.autonavi.com/showpic/1ecb2ff77658d510ed433c5505a3dc25', 12900, 21800, 0, 1),
(1000344, 161, '基础单色美甲', '任选单色 · 含护理', 'http://store.is.autonavi.com/showpic/2ef94ba181820be9811197d6853eb267', 3900, 7800, 1, 1),
(1000345, 161, '美甲+美睫套餐', '单色美甲+自然款美睫', 'http://store.is.autonavi.com/showpic/2ef94ba181820be9811197d6853eb267', 12900, 21800, 0, 1),
(1000346, 162, '基础单色美甲', '任选单色 · 含护理', 'https://store.is.autonavi.com/showpic/6fafacf48a352ecfb7c8855614889d3c', 3900, 7800, 1, 1),
(1000347, 162, '美甲+美睫套餐', '单色美甲+自然款美睫', 'https://store.is.autonavi.com/showpic/6fafacf48a352ecfb7c8855614889d3c', 12900, 21800, 0, 1),
(1000348, 163, '基础单色美甲', '任选单色 · 含护理', 'https://store.is.autonavi.com/showpic/9a73906d971ca191c2eb187e9e5b5061', 3900, 7800, 1, 1),
(1000349, 163, '美甲+美睫套餐', '单色美甲+自然款美睫', 'https://store.is.autonavi.com/showpic/9a73906d971ca191c2eb187e9e5b5061', 12900, 21800, 0, 1),
(1000350, 164, '基础单色美甲', '任选单色 · 含护理', 'https://aos-comment.amap.com/B0K0B5M19L/comment/1C65AF60_5603_47FA_9238_7FBA4FBCC0BF_L0_001_1692_2002_1742111427793_44265083.jpg', 3900, 7800, 1, 1),
(1000351, 164, '美甲+美睫套餐', '单色美甲+自然款美睫', 'https://aos-comment.amap.com/B0K0B5M19L/comment/1C65AF60_5603_47FA_9238_7FBA4FBCC0BF_L0_001_1692_2002_1742111427793_44265083.jpg', 12900, 21800, 0, 1),
(1000352, 165, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://store.is.autonavi.com/showpic/6214e0e521761207dc782d4bb5af16c2', 12800, 18800, 1, 1),
(1000353, 165, '单人豪华餐', '单人精选 · 主食+饮品', 'https://store.is.autonavi.com/showpic/6214e0e521761207dc782d4bb5af16c2', 6800, 9800, 0, 1),
(1000354, 165, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://store.is.autonavi.com/showpic/6214e0e521761207dc782d4bb5af16c2', 15800, 22800, 0, 1),
(1000355, 166, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://aos-comment.amap.com/B0J0PL7CJT/comment/5D82B619_5131_4231_93B2_3B95E2ACB718_L0_001_1500_200_1769315899767_88209845.jpg', 12800, 18800, 1, 1),
(1000356, 166, '单人豪华餐', '单人精选 · 主食+饮品', 'https://aos-comment.amap.com/B0J0PL7CJT/comment/5D82B619_5131_4231_93B2_3B95E2ACB718_L0_001_1500_200_1769315899767_88209845.jpg', 6800, 9800, 0, 1),
(1000357, 166, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://aos-comment.amap.com/B0J0PL7CJT/comment/5D82B619_5131_4231_93B2_3B95E2ACB718_L0_001_1500_200_1769315899767_88209845.jpg', 15800, 22800, 0, 1),
(1000358, 167, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://store.is.autonavi.com/showpic/d097d0034048c1750000002619238479?type=pic', 12800, 18800, 1, 1),
(1000359, 167, '单人豪华餐', '单人精选 · 主食+饮品', 'https://store.is.autonavi.com/showpic/d097d0034048c1750000002619238479?type=pic', 6800, 9800, 0, 1),
(1000360, 167, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://store.is.autonavi.com/showpic/d097d0034048c1750000002619238479?type=pic', 15800, 22800, 0, 1),
(1000361, 168, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'http://store.is.autonavi.com/query_pic?id=st325047e1-db2f-4764-9003-44982546dfb9&user=search&operate=original', 12800, 18800, 1, 1),
(1000362, 168, '单人豪华餐', '单人精选 · 主食+饮品', 'http://store.is.autonavi.com/query_pic?id=st325047e1-db2f-4764-9003-44982546dfb9&user=search&operate=original', 6800, 9800, 0, 1),
(1000363, 168, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'http://store.is.autonavi.com/query_pic?id=st325047e1-db2f-4764-9003-44982546dfb9&user=search&operate=original', 15800, 22800, 0, 1),
(1000364, 169, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://aos-comment.amap.com/B0LB3SBLK0/comment/content_media_external_file_1000026991_ss__1755232241737_30348753.jpg', 12800, 18800, 1, 1),
(1000365, 169, '单人豪华餐', '单人精选 · 主食+饮品', 'https://aos-comment.amap.com/B0LB3SBLK0/comment/content_media_external_file_1000026991_ss__1755232241737_30348753.jpg', 6800, 9800, 0, 1),
(1000366, 169, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://aos-comment.amap.com/B0LB3SBLK0/comment/content_media_external_file_1000026991_ss__1755232241737_30348753.jpg', 15800, 22800, 0, 1),
(1000367, 170, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://aos-comment.amap.com/B0FFI1X3L9/comment/content_media_external_file_1000026998_ss__1755232332901_23023174.jpg', 12800, 18800, 1, 1),
(1000368, 170, '单人豪华餐', '单人精选 · 主食+饮品', 'https://aos-comment.amap.com/B0FFI1X3L9/comment/content_media_external_file_1000026998_ss__1755232332901_23023174.jpg', 6800, 9800, 0, 1),
(1000369, 170, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://aos-comment.amap.com/B0FFI1X3L9/comment/content_media_external_file_1000026998_ss__1755232332901_23023174.jpg', 15800, 22800, 0, 1),
(1000370, 171, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://aos-comment.amap.com/B0GKM5RRE1/comment/%5B2026-01-13-12-34-29%5D14BC8A9D-7594-4F2C-BFDD-C5B463D19ED_1768278892146_68867422.jpg', 12800, 18800, 1, 1),
(1000371, 171, '单人豪华餐', '单人精选 · 主食+饮品', 'https://aos-comment.amap.com/B0GKM5RRE1/comment/%5B2026-01-13-12-34-29%5D14BC8A9D-7594-4F2C-BFDD-C5B463D19ED_1768278892146_68867422.jpg', 6800, 9800, 0, 1),
(1000372, 171, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://aos-comment.amap.com/B0GKM5RRE1/comment/%5B2026-01-13-12-34-29%5D14BC8A9D-7594-4F2C-BFDD-C5B463D19ED_1768278892146_68867422.jpg', 15800, 22800, 0, 1),
(1000373, 172, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'http://store.is.autonavi.com/showpic/845354bf81bb0800e627b89f34b7e281', 12800, 18800, 1, 1),
(1000374, 172, '单人豪华餐', '单人精选 · 主食+饮品', 'http://store.is.autonavi.com/showpic/845354bf81bb0800e627b89f34b7e281', 6800, 9800, 0, 1),
(1000375, 172, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'http://store.is.autonavi.com/showpic/845354bf81bb0800e627b89f34b7e281', 15800, 22800, 0, 1),
(1000376, 173, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://store.is.autonavi.com/showpic/c287706810a95e84ab635a931ccf264d?operate=original', 12800, 18800, 1, 1),
(1000377, 173, '单人豪华餐', '单人精选 · 主食+饮品', 'https://store.is.autonavi.com/showpic/c287706810a95e84ab635a931ccf264d?operate=original', 6800, 9800, 0, 1),
(1000378, 173, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://store.is.autonavi.com/showpic/c287706810a95e84ab635a931ccf264d?operate=original', 15800, 22800, 0, 1),
(1000379, 174, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://aos-comment.amap.com/B0I1GR03L1/comment/content_media_external_images_media_1000009639_ss__1766888780714_14951665.jpg', 12800, 18800, 1, 1),
(1000380, 174, '单人豪华餐', '单人精选 · 主食+饮品', 'https://aos-comment.amap.com/B0I1GR03L1/comment/content_media_external_images_media_1000009639_ss__1766888780714_14951665.jpg', 6800, 9800, 0, 1),
(1000381, 174, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://aos-comment.amap.com/B0I1GR03L1/comment/content_media_external_images_media_1000009639_ss__1766888780714_14951665.jpg', 15800, 22800, 0, 1),
(1000382, 175, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://aos-comment.amap.com/B0J2DDJFVO/comment/ae9fe10e8340edd4d88af95282104a5c_2048_2048_80.jpg', 12800, 18800, 1, 1),
(1000383, 175, '单人豪华餐', '单人精选 · 主食+饮品', 'https://aos-comment.amap.com/B0J2DDJFVO/comment/ae9fe10e8340edd4d88af95282104a5c_2048_2048_80.jpg', 6800, 9800, 0, 1),
(1000384, 175, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://aos-comment.amap.com/B0J2DDJFVO/comment/ae9fe10e8340edd4d88af95282104a5c_2048_2048_80.jpg', 15800, 22800, 0, 1),
(1000385, 176, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'http://store.is.autonavi.com/showpic/10fabd1642a7b0141ecf246994a7300d', 12800, 18800, 1, 1),
(1000386, 176, '单人豪华餐', '单人精选 · 主食+饮品', 'http://store.is.autonavi.com/showpic/10fabd1642a7b0141ecf246994a7300d', 6800, 9800, 0, 1),
(1000387, 176, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'http://store.is.autonavi.com/showpic/10fabd1642a7b0141ecf246994a7300d', 15800, 22800, 0, 1),
(1000388, 177, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'http://store.is.autonavi.com/showpic/4858c8ab6890288140c4f74641d48c3b', 12800, 18800, 1, 1),
(1000389, 177, '单人豪华餐', '单人精选 · 主食+饮品', 'http://store.is.autonavi.com/showpic/4858c8ab6890288140c4f74641d48c3b', 6800, 9800, 0, 1),
(1000390, 177, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'http://store.is.autonavi.com/showpic/4858c8ab6890288140c4f74641d48c3b', 15800, 22800, 0, 1),
(1000391, 178, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'http://store.is.autonavi.com/showpic/2907b0f421cf3718236e6be6c546a432', 12800, 18800, 1, 1),
(1000392, 178, '单人豪华餐', '单人精选 · 主食+饮品', 'http://store.is.autonavi.com/showpic/2907b0f421cf3718236e6be6c546a432', 6800, 9800, 0, 1),
(1000393, 178, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'http://store.is.autonavi.com/showpic/2907b0f421cf3718236e6be6c546a432', 15800, 22800, 0, 1),
(1000394, 179, '冬日双人餐', '精选双人套餐 · 适合2-3人', 'https://store.is.autonavi.com/showpic/b296e293c3ac1127dd4fda3644615edf', 12800, 18800, 1, 1),
(1000395, 179, '单人豪华餐', '单人精选 · 主食+饮品', 'https://store.is.autonavi.com/showpic/b296e293c3ac1127dd4fda3644615edf', 6800, 9800, 0, 1),
(1000396, 179, '双人欢享套餐', '招牌菜双人份 · 到店即用', 'https://store.is.autonavi.com/showpic/b296e293c3ac1127dd4fda3644615edf', 15800, 22800, 0, 1),
(1000397, 180, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'http://store.is.autonavi.com/query_pic?id=st77d091b5-b1e2-4e76-be7b-85696a809271&user=search&operate=original', 9900, 16800, 1, 1),
(1000398, 180, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'http://store.is.autonavi.com/query_pic?id=st77d091b5-b1e2-4e76-be7b-85696a809271&user=search&operate=original', 13900, 22800, 0, 1),
(1000399, 181, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://aos-comment.amap.com/B0LDLHRVLY/comment/176231817367_1762318174982_62643483.jpg', 9900, 16800, 1, 1),
(1000400, 181, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://aos-comment.amap.com/B0LDLHRVLY/comment/176231817367_1762318174982_62643483.jpg', 13900, 22800, 0, 1),
(1000401, 182, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://store.is.autonavi.com/showpic/47341e0857472015ea26587e4c1b3c8a', 9900, 16800, 1, 1),
(1000402, 182, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://store.is.autonavi.com/showpic/47341e0857472015ea26587e4c1b3c8a', 13900, 22800, 0, 1),
(1000403, 183, '欢唱3小时套餐', '含果盘 · 非节假日可用', '', 9900, 16800, 1, 1),
(1000404, 183, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', '', 13900, 22800, 0, 1),
(1000405, 184, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://aos-comment.amap.com/B0IB1HEGZ7/comment/20353a1ab639a9c9e1ecd0445f90ccce_2048_2048_80.jpg', 9900, 16800, 1, 1),
(1000406, 184, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://aos-comment.amap.com/B0IB1HEGZ7/comment/20353a1ab639a9c9e1ecd0445f90ccce_2048_2048_80.jpg', 13900, 22800, 0, 1),
(1000407, 185, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://aos-comment.amap.com/B0FFLGM1WF/comment/2aa478da3bf63a80887e99122c3d7912_2048_2048_80.jpg', 9900, 16800, 1, 1),
(1000408, 185, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://aos-comment.amap.com/B0FFLGM1WF/comment/2aa478da3bf63a80887e99122c3d7912_2048_2048_80.jpg', 13900, 22800, 0, 1),
(1000409, 186, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://aos-comment.amap.com/B024F05O4U/comment/2bb061a446f111d8b6a85d806520f81b_2048_2048_80.jpg', 9900, 16800, 1, 1),
(1000410, 186, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://aos-comment.amap.com/B024F05O4U/comment/2bb061a446f111d8b6a85d806520f81b_2048_2048_80.jpg', 13900, 22800, 0, 1),
(1000411, 187, '欢唱3小时套餐', '含果盘 · 非节假日可用', '', 9900, 16800, 1, 1),
(1000412, 187, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', '', 13900, 22800, 0, 1),
(1000413, 188, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://aos-comment.amap.com/B0K16AVBCK/comment/47D46675_D935_4DFC_8860_2402CE12C879_L0_001_1206_160_1769617087791_11247678.jpg', 9900, 16800, 1, 1),
(1000414, 188, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://aos-comment.amap.com/B0K16AVBCK/comment/47D46675_D935_4DFC_8860_2402CE12C879_L0_001_1206_160_1769617087791_11247678.jpg', 13900, 22800, 0, 1),
(1000415, 189, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://aos-comment.amap.com/B0H2UUR2A2/comment/a0edd95f8848dd0cc50e16896f80240c_2048_2048_80.jpg', 9900, 16800, 1, 1),
(1000416, 189, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://aos-comment.amap.com/B0H2UUR2A2/comment/a0edd95f8848dd0cc50e16896f80240c_2048_2048_80.jpg', 13900, 22800, 0, 1),
(1000417, 190, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://store.is.autonavi.com/showpic/d1858d9ab897a9a0ce3767cec75ddecb', 9900, 16800, 1, 1),
(1000418, 190, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://store.is.autonavi.com/showpic/d1858d9ab897a9a0ce3767cec75ddecb', 13900, 22800, 0, 1),
(1000419, 191, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://store.is.autonavi.com/showpic/30b87b77039129f00000002241796093?type=pic', 9900, 16800, 1, 1),
(1000420, 191, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://store.is.autonavi.com/showpic/30b87b77039129f00000002241796093?type=pic', 13900, 22800, 0, 1),
(1000421, 192, '欢唱3小时套餐', '含果盘 · 非节假日可用', '', 9900, 16800, 1, 1),
(1000422, 192, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', '', 13900, 22800, 0, 1),
(1000423, 193, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://aos-comment.amap.com/B0FFLAVO9P/comment/2bb061a446f111d8b6a85d806520f81b_2048_2048_80.jpg', 9900, 16800, 1, 1),
(1000424, 193, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://aos-comment.amap.com/B0FFLAVO9P/comment/2bb061a446f111d8b6a85d806520f81b_2048_2048_80.jpg', 13900, 22800, 0, 1),
(1000425, 194, '欢唱3小时套餐', '含果盘 · 非节假日可用', 'https://aos-comment.amap.com/B0FFKHMWV5/comment/content_media_external_file_1000090530_ss__1761836273825_40198046.jpg', 9900, 16800, 1, 1),
(1000426, 194, '欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 'https://aos-comment.amap.com/B0FFKHMWV5/comment/content_media_external_file_1000090530_ss__1761836273825_40198046.jpg', 13900, 22800, 0, 1),
(1000427, 195, '洗剪吹套餐', '含洗头+剪发+造型', '', 5800, 9800, 1, 1),
(1000428, 195, '烫发+护理套餐', '含洗剪吹 · 需预约', '', 16800, 28800, 0, 1),
(1000429, 196, '洗剪吹套餐', '含洗头+剪发+造型', '', 5800, 9800, 1, 1),
(1000430, 196, '烫发+护理套餐', '含洗剪吹 · 需预约', '', 16800, 28800, 0, 1),
(1000431, 197, '洗剪吹套餐', '含洗头+剪发+造型', 'http://store.is.autonavi.com/showpic/751c067b3a76bda7c595bb7384c842fd', 5800, 9800, 1, 1),
(1000432, 197, '烫发+护理套餐', '含洗剪吹 · 需预约', 'http://store.is.autonavi.com/showpic/751c067b3a76bda7c595bb7384c842fd', 16800, 28800, 0, 1),
(1000433, 198, '洗剪吹套餐', '含洗头+剪发+造型', 'http://store.is.autonavi.com/showpic/d4de3bc9da86faad0bfb16abcb195a60', 5800, 9800, 1, 1),
(1000434, 198, '烫发+护理套餐', '含洗剪吹 · 需预约', 'http://store.is.autonavi.com/showpic/d4de3bc9da86faad0bfb16abcb195a60', 16800, 28800, 0, 1),
(1000435, 199, '洗剪吹套餐', '含洗头+剪发+造型', 'http://store.is.autonavi.com/showpic/149b2f84e812281e91d44414541fc0ae', 5800, 9800, 1, 1),
(1000436, 199, '烫发+护理套餐', '含洗剪吹 · 需预约', 'http://store.is.autonavi.com/showpic/149b2f84e812281e91d44414541fc0ae', 16800, 28800, 0, 1),
(1000437, 200, '洗剪吹套餐', '含洗头+剪发+造型', '', 5800, 9800, 1, 1),
(1000438, 200, '烫发+护理套餐', '含洗剪吹 · 需预约', '', 16800, 28800, 0, 1),
(1000439, 201, '洗剪吹套餐', '含洗头+剪发+造型', '', 5800, 9800, 1, 1),
(1000440, 201, '烫发+护理套餐', '含洗剪吹 · 需预约', '', 16800, 28800, 0, 1),
(1000441, 202, '洗剪吹套餐', '含洗头+剪发+造型', '', 5800, 9800, 1, 1),
(1000442, 202, '烫发+护理套餐', '含洗剪吹 · 需预约', '', 16800, 28800, 0, 1),
(1000443, 203, '洗剪吹套餐', '含洗头+剪发+造型', 'http://store.is.autonavi.com/showpic/42f3d364386b2959e5f5485d45ddf629', 5800, 9800, 1, 1),
(1000444, 203, '烫发+护理套餐', '含洗剪吹 · 需预约', 'http://store.is.autonavi.com/showpic/42f3d364386b2959e5f5485d45ddf629', 16800, 28800, 0, 1),
(1000445, 204, '洗剪吹套餐', '含洗头+剪发+造型', 'http://store.is.autonavi.com/showpic/12ec13ce315895d8e13099f309d0583b', 5800, 9800, 1, 1),
(1000446, 204, '烫发+护理套餐', '含洗剪吹 · 需预约', 'http://store.is.autonavi.com/showpic/12ec13ce315895d8e13099f309d0583b', 16800, 28800, 0, 1),
(1000447, 205, '洗剪吹套餐', '含洗头+剪发+造型', 'http://store.is.autonavi.com/showpic/bb33173c9897d1b3eae30477c4384ef7', 5800, 9800, 1, 1),
(1000448, 205, '烫发+护理套餐', '含洗剪吹 · 需预约', 'http://store.is.autonavi.com/showpic/bb33173c9897d1b3eae30477c4384ef7', 16800, 28800, 0, 1),
(1000449, 206, '洗剪吹套餐', '含洗头+剪发+造型', 'http://store.is.autonavi.com/showpic/f327dd7770559d4e487d3782255a8b3b', 5800, 9800, 1, 1),
(1000450, 206, '烫发+护理套餐', '含洗剪吹 · 需预约', 'http://store.is.autonavi.com/showpic/f327dd7770559d4e487d3782255a8b3b', 16800, 28800, 0, 1),
(1000451, 207, '洗剪吹套餐', '含洗头+剪发+造型', '', 5800, 9800, 1, 1),
(1000452, 207, '烫发+护理套餐', '含洗剪吹 · 需预约', '', 16800, 28800, 0, 1),
(1000453, 208, '洗剪吹套餐', '含洗头+剪发+造型', 'http://store.is.autonavi.com/query_pic?id=st8d5fafd8-9f0f-4a0f-a6c1-31912f510578&user=search&operate=original', 5800, 9800, 1, 1),
(1000454, 208, '烫发+护理套餐', '含洗剪吹 · 需预约', 'http://store.is.autonavi.com/query_pic?id=st8d5fafd8-9f0f-4a0f-a6c1-31912f510578&user=search&operate=original', 16800, 28800, 0, 1),
(1000455, 209, '洗剪吹套餐', '含洗头+剪发+造型', 'http://store.is.autonavi.com/showpic/2ae5d416cc6a1594a709710fe51a3a47', 5800, 9800, 1, 1),
(1000456, 209, '烫发+护理套餐', '含洗剪吹 · 需预约', 'http://store.is.autonavi.com/showpic/2ae5d416cc6a1594a709710fe51a3a47', 16800, 28800, 0, 1),
(1000457, 210, '单次体验卡', '器械+团课任选一次', '', 2900, 5800, 1, 1),
(1000458, 210, '健身月卡', '30天不限次 · 含团课', '', 19900, 39900, 0, 1),
(1000459, 211, '单次体验卡', '器械+团课任选一次', 'http://store.is.autonavi.com/showpic/57650dc449f5002f4500a7bb77cd6e46', 2900, 5800, 1, 1),
(1000460, 211, '健身月卡', '30天不限次 · 含团课', 'http://store.is.autonavi.com/showpic/57650dc449f5002f4500a7bb77cd6e46', 19900, 39900, 0, 1),
(1000461, 212, '单次体验卡', '器械+团课任选一次', 'https://aos-comment.amap.com/B0H0JDV0US/comment/332D3544_5C1C_497C_8703_F74461363A3F_L0_001_1500_200_1759647264667_36130266.jpg', 2900, 5800, 1, 1),
(1000462, 212, '健身月卡', '30天不限次 · 含团课', 'https://aos-comment.amap.com/B0H0JDV0US/comment/332D3544_5C1C_497C_8703_F74461363A3F_L0_001_1500_200_1759647264667_36130266.jpg', 19900, 39900, 0, 1),
(1000463, 213, '单次体验卡', '器械+团课任选一次', 'https://store.is.autonavi.com/showpic/d5f09a8a9da43e760000002876566919?type=pic', 2900, 5800, 1, 1),
(1000464, 213, '健身月卡', '30天不限次 · 含团课', 'https://store.is.autonavi.com/showpic/d5f09a8a9da43e760000002876566919?type=pic', 19900, 39900, 0, 1),
(1000465, 214, '单次体验卡', '器械+团课任选一次', '', 2900, 5800, 1, 1),
(1000466, 214, '健身月卡', '30天不限次 · 含团课', '', 19900, 39900, 0, 1),
(1000467, 215, '单次体验卡', '器械+团课任选一次', '', 2900, 5800, 1, 1),
(1000468, 215, '健身月卡', '30天不限次 · 含团课', '', 19900, 39900, 0, 1),
(1000469, 216, '单次体验卡', '器械+团课任选一次', 'https://aos-comment.amap.com/B0G1ZR6I3R/comment/20464731c64086fa30f8082cc7a91d8e_2048_2048_80.jpg', 2900, 5800, 1, 1),
(1000470, 216, '健身月卡', '30天不限次 · 含团课', 'https://aos-comment.amap.com/B0G1ZR6I3R/comment/20464731c64086fa30f8082cc7a91d8e_2048_2048_80.jpg', 19900, 39900, 0, 1),
(1000471, 217, '单次体验卡', '器械+团课任选一次', '', 2900, 5800, 1, 1),
(1000472, 217, '健身月卡', '30天不限次 · 含团课', '', 19900, 39900, 0, 1),
(1000473, 218, '单次体验卡', '器械+团课任选一次', 'https://store.is.autonavi.com/showpic/227b29b5230f372d74cd342f2c00b4e4', 2900, 5800, 1, 1),
(1000474, 218, '健身月卡', '30天不限次 · 含团课', 'https://store.is.autonavi.com/showpic/227b29b5230f372d74cd342f2c00b4e4', 19900, 39900, 0, 1),
(1000475, 219, '单次体验卡', '器械+团课任选一次', '', 2900, 5800, 1, 1),
(1000476, 219, '健身月卡', '30天不限次 · 含团课', '', 19900, 39900, 0, 1),
(1000477, 220, '单次体验卡', '器械+团课任选一次', 'https://store.is.autonavi.com/showpic/ea30976e3db68b6b14232f1d1a01af00', 2900, 5800, 1, 1),
(1000478, 220, '健身月卡', '30天不限次 · 含团课', 'https://store.is.autonavi.com/showpic/ea30976e3db68b6b14232f1d1a01af00', 19900, 39900, 0, 1),
(1000479, 221, '单次体验卡', '器械+团课任选一次', 'https://aos-comment.amap.com/B0I1XCMAA2/comment/0fdad18b1971dd89af4c0679e609ee54_2048_2048_80.jpg', 2900, 5800, 1, 1),
(1000480, 221, '健身月卡', '30天不限次 · 含团课', 'https://aos-comment.amap.com/B0I1XCMAA2/comment/0fdad18b1971dd89af4c0679e609ee54_2048_2048_80.jpg', 19900, 39900, 0, 1),
(1000481, 222, '单次体验卡', '器械+团课任选一次', 'https://store.is.autonavi.com/showpic/4b7b0034d22bd771accd1a344daf6e09', 2900, 5800, 1, 1),
(1000482, 222, '健身月卡', '30天不限次 · 含团课', 'https://store.is.autonavi.com/showpic/4b7b0034d22bd771accd1a344daf6e09', 19900, 39900, 0, 1),
(1000483, 223, '单次体验卡', '器械+团课任选一次', 'https://store.is.autonavi.com/showpic/9a47af9ef0b2cf42c1d310d3b21bd3f8', 2900, 5800, 1, 1),
(1000484, 223, '健身月卡', '30天不限次 · 含团课', 'https://store.is.autonavi.com/showpic/9a47af9ef0b2cf42c1d310d3b21bd3f8', 19900, 39900, 0, 1),
(1000485, 224, '单次体验卡', '器械+团课任选一次', '', 2900, 5800, 1, 1),
(1000486, 224, '健身月卡', '30天不限次 · 含团课', '', 19900, 39900, 0, 1),
(1000487, 225, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/a3818b210b36ad670000000731128854?type=pic', 6900, 12800, 1, 1),
(1000488, 225, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/a3818b210b36ad670000000731128854?type=pic', 12800, 22800, 0, 1),
(1000489, 226, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/d5f8b32b2ee934ec0000001446698159?type=pic', 6900, 12800, 1, 1),
(1000490, 226, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/d5f8b32b2ee934ec0000001446698159?type=pic', 12800, 22800, 0, 1),
(1000491, 227, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/692560be2fbaab2ee903dc23669822fa', 6900, 12800, 1, 1),
(1000492, 227, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/692560be2fbaab2ee903dc23669822fa', 12800, 22800, 0, 1),
(1000493, 228, '足疗60分钟', '传统足疗 · 赠茶点', '', 6900, 12800, 1, 1),
(1000494, 228, '全身按摩套餐', '90分钟全身SPA按摩', '', 12800, 22800, 0, 1),
(1000495, 229, '足疗60分钟', '传统足疗 · 赠茶点', 'http://store.is.autonavi.com/showpic/8ea5735b6b770b06e684e6fbabfd51ee', 6900, 12800, 1, 1),
(1000496, 229, '全身按摩套餐', '90分钟全身SPA按摩', 'http://store.is.autonavi.com/showpic/8ea5735b6b770b06e684e6fbabfd51ee', 12800, 22800, 0, 1),
(1000497, 230, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/dc50d85d2d101b674b5224b261b9f27b', 6900, 12800, 1, 1),
(1000498, 230, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/dc50d85d2d101b674b5224b261b9f27b', 12800, 22800, 0, 1),
(1000499, 231, '足疗60分钟', '传统足疗 · 赠茶点', '', 6900, 12800, 1, 1),
(1000500, 231, '全身按摩套餐', '90分钟全身SPA按摩', '', 12800, 22800, 0, 1),
(1000501, 232, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/4bd28bc46b0503dbd07ed5f1745e7ffc', 6900, 12800, 1, 1),
(1000502, 232, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/4bd28bc46b0503dbd07ed5f1745e7ffc', 12800, 22800, 0, 1),
(1000503, 233, '足疗60分钟', '传统足疗 · 赠茶点', '', 6900, 12800, 1, 1),
(1000504, 233, '全身按摩套餐', '90分钟全身SPA按摩', '', 12800, 22800, 0, 1),
(1000505, 234, '足疗60分钟', '传统足疗 · 赠茶点', 'http://store.is.autonavi.com/showpic/9bb8ab31c560288f6903888fe405a96a', 6900, 12800, 1, 1),
(1000506, 234, '全身按摩套餐', '90分钟全身SPA按摩', 'http://store.is.autonavi.com/showpic/9bb8ab31c560288f6903888fe405a96a', 12800, 22800, 0, 1),
(1000507, 235, '足疗60分钟', '传统足疗 · 赠茶点', 'http://store.is.autonavi.com/showpic/ef5b4f518e064780936443c216a30dfc', 6900, 12800, 1, 1),
(1000508, 235, '全身按摩套餐', '90分钟全身SPA按摩', 'http://store.is.autonavi.com/showpic/ef5b4f518e064780936443c216a30dfc', 12800, 22800, 0, 1),
(1000509, 236, '足疗60分钟', '传统足疗 · 赠茶点', 'http://store.is.autonavi.com/showpic/215c9bfece5c732ed817755cdf52ee0e', 6900, 12800, 1, 1),
(1000510, 236, '全身按摩套餐', '90分钟全身SPA按摩', 'http://store.is.autonavi.com/showpic/215c9bfece5c732ed817755cdf52ee0e', 12800, 22800, 0, 1),
(1000511, 237, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/3c8f48457756909e0000003373008640?type=pic', 6900, 12800, 1, 1),
(1000512, 237, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/3c8f48457756909e0000003373008640?type=pic', 12800, 22800, 0, 1),
(1000513, 238, '足疗60分钟', '传统足疗 · 赠茶点', 'https://store.is.autonavi.com/showpic/873fca38d3cc1f7c5e900f0eea10a2ab', 6900, 12800, 1, 1),
(1000514, 238, '全身按摩套餐', '90分钟全身SPA按摩', 'https://store.is.autonavi.com/showpic/873fca38d3cc1f7c5e900f0eea10a2ab', 12800, 22800, 0, 1),
(1000515, 239, '足疗60分钟', '传统足疗 · 赠茶点', 'https://aos-comment.amap.com/B0KUG1LAGG/comment/content_media_external_file_100001500_1762188851020_63604713.jpg', 6900, 12800, 1, 1),
(1000516, 239, '全身按摩套餐', '90分钟全身SPA按摩', 'https://aos-comment.amap.com/B0KUG1LAGG/comment/content_media_external_file_100001500_1762188851020_63604713.jpg', 12800, 22800, 0, 1),
(1000517, 240, '面部深层护理', '清洁+补水+按摩', '', 15900, 28800, 1, 1),
(1000518, 240, '全身SPA套餐', '120分钟全身放松', '', 25900, 42800, 0, 1),
(1000519, 241, '面部深层护理', '清洁+补水+按摩', '', 15900, 28800, 1, 1),
(1000520, 241, '全身SPA套餐', '120分钟全身放松', '', 25900, 42800, 0, 1),
(1000521, 242, '面部深层护理', '清洁+补水+按摩', 'http://store.is.autonavi.com/showpic/751c067b3a76bda7c595bb7384c842fd', 15900, 28800, 1, 1),
(1000522, 242, '全身SPA套餐', '120分钟全身放松', 'http://store.is.autonavi.com/showpic/751c067b3a76bda7c595bb7384c842fd', 25900, 42800, 0, 1),
(1000523, 243, '面部深层护理', '清洁+补水+按摩', 'http://store.is.autonavi.com/showpic/d4de3bc9da86faad0bfb16abcb195a60', 15900, 28800, 1, 1),
(1000524, 243, '全身SPA套餐', '120分钟全身放松', 'http://store.is.autonavi.com/showpic/d4de3bc9da86faad0bfb16abcb195a60', 25900, 42800, 0, 1),
(1000525, 244, '面部深层护理', '清洁+补水+按摩', 'http://store.is.autonavi.com/showpic/149b2f84e812281e91d44414541fc0ae', 15900, 28800, 1, 1),
(1000526, 244, '全身SPA套餐', '120分钟全身放松', 'http://store.is.autonavi.com/showpic/149b2f84e812281e91d44414541fc0ae', 25900, 42800, 0, 1),
(1000527, 245, '面部深层护理', '清洁+补水+按摩', '', 15900, 28800, 1, 1),
(1000528, 245, '全身SPA套餐', '120分钟全身放松', '', 25900, 42800, 0, 1),
(1000529, 246, '面部深层护理', '清洁+补水+按摩', '', 15900, 28800, 1, 1),
(1000530, 246, '全身SPA套餐', '120分钟全身放松', '', 25900, 42800, 0, 1),
(1000531, 247, '面部深层护理', '清洁+补水+按摩', '', 15900, 28800, 1, 1),
(1000532, 247, '全身SPA套餐', '120分钟全身放松', '', 25900, 42800, 0, 1),
(1000533, 248, '面部深层护理', '清洁+补水+按摩', 'https://aos-comment.amap.com/B0KAO9Z256/comment/content_media_external_images_media_2180_1726845106545_44446883.jpg', 15900, 28800, 1, 1),
(1000534, 248, '全身SPA套餐', '120分钟全身放松', 'https://aos-comment.amap.com/B0KAO9Z256/comment/content_media_external_images_media_2180_1726845106545_44446883.jpg', 25900, 42800, 0, 1),
(1000535, 249, '面部深层护理', '清洁+补水+按摩', 'http://store.is.autonavi.com/showpic/1a939c2c714aa97b03e4380a0f4ee4e0', 15900, 28800, 1, 1),
(1000536, 249, '全身SPA套餐', '120分钟全身放松', 'http://store.is.autonavi.com/showpic/1a939c2c714aa97b03e4380a0f4ee4e0', 25900, 42800, 0, 1),
(1000537, 250, '面部深层护理', '清洁+补水+按摩', 'http://store.is.autonavi.com/showpic/829e74e24d25b73840ecc000cb83c417', 15900, 28800, 1, 1),
(1000538, 250, '全身SPA套餐', '120分钟全身放松', 'http://store.is.autonavi.com/showpic/829e74e24d25b73840ecc000cb83c417', 25900, 42800, 0, 1),
(1000539, 251, '面部深层护理', '清洁+补水+按摩', 'https://aos-comment.amap.com/B0H121DL3Q/comment/FEF2B4B7_F6FD_4E0C_BD69_AD988B8BC028_L0_001_1500_200_1763568677731_15973317.jpg', 15900, 28800, 1, 1),
(1000540, 251, '全身SPA套餐', '120分钟全身放松', 'https://aos-comment.amap.com/B0H121DL3Q/comment/FEF2B4B7_F6FD_4E0C_BD69_AD988B8BC028_L0_001_1500_200_1763568677731_15973317.jpg', 25900, 42800, 0, 1),
(1000541, 252, '面部深层护理', '清洁+补水+按摩', 'https://aos-comment.amap.com/comment/content_service__1770105629734_19236017_1770105643036_51097957.jpg', 15900, 28800, 1, 1),
(1000542, 252, '全身SPA套餐', '120分钟全身放松', 'https://aos-comment.amap.com/comment/content_service__1770105629734_19236017_1770105643036_51097957.jpg', 25900, 42800, 0, 1),
(1000543, 253, '面部深层护理', '清洁+补水+按摩', 'https://store.is.autonavi.com/showpic/84a2bf64d16092550845d98d6e2ee2bf', 15900, 28800, 1, 1),
(1000544, 253, '全身SPA套餐', '120分钟全身放松', 'https://store.is.autonavi.com/showpic/84a2bf64d16092550845d98d6e2ee2bf', 25900, 42800, 0, 1),
(1000545, 254, '面部深层护理', '清洁+补水+按摩', 'https://aos-comment.amap.com/B0K23U0Y28/comment/86B379D1_0F3A_4C56_8D40_5518194455DE_L0_001_1500_200_1749612390053_98289755.jpg', 15900, 28800, 1, 1),
(1000546, 254, '全身SPA套餐', '120分钟全身放松', 'https://aos-comment.amap.com/B0K23U0Y28/comment/86B379D1_0F3A_4C56_8D40_5518194455DE_L0_001_1500_200_1749612390053_98289755.jpg', 25900, 42800, 0, 1),
(1000547, 255, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'https://store.is.autonavi.com/showpic/c85518898aa1dfd07fe8a100cc801339', 5900, 9900, 1, 1),
(1000548, 255, '亲子全天通票', '全天不限时 · 含家长陪同', 'https://store.is.autonavi.com/showpic/c85518898aa1dfd07fe8a100cc801339', 9900, 16800, 0, 1),
(1000549, 256, '亲子畅玩2小时', '海洋球+滑梯+沙池', '', 5900, 9900, 1, 1),
(1000550, 256, '亲子全天通票', '全天不限时 · 含家长陪同', '', 9900, 16800, 0, 1),
(1000551, 257, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'http://store.is.autonavi.com/showpic/e3bde239b3b978a09c000cd11276039e', 5900, 9900, 1, 1),
(1000552, 257, '亲子全天通票', '全天不限时 · 含家长陪同', 'http://store.is.autonavi.com/showpic/e3bde239b3b978a09c000cd11276039e', 9900, 16800, 0, 1),
(1000553, 258, '亲子畅玩2小时', '海洋球+滑梯+沙池', '', 5900, 9900, 1, 1),
(1000554, 258, '亲子全天通票', '全天不限时 · 含家长陪同', '', 9900, 16800, 0, 1),
(1000555, 259, '亲子畅玩2小时', '海洋球+滑梯+沙池', '', 5900, 9900, 1, 1),
(1000556, 259, '亲子全天通票', '全天不限时 · 含家长陪同', '', 9900, 16800, 0, 1),
(1000557, 260, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'http://store.is.autonavi.com/showpic/b6fb25a6b47c77bb12f212265923f906', 5900, 9900, 1, 1),
(1000558, 260, '亲子全天通票', '全天不限时 · 含家长陪同', 'http://store.is.autonavi.com/showpic/b6fb25a6b47c77bb12f212265923f906', 9900, 16800, 0, 1),
(1000559, 261, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'http://store.is.autonavi.com/showpic/7a9fd8e04fad48bed38038d01c065762', 5900, 9900, 1, 1),
(1000560, 261, '亲子全天通票', '全天不限时 · 含家长陪同', 'http://store.is.autonavi.com/showpic/7a9fd8e04fad48bed38038d01c065762', 9900, 16800, 0, 1),
(1000561, 262, '亲子畅玩2小时', '海洋球+滑梯+沙池', '', 5900, 9900, 1, 1),
(1000562, 262, '亲子全天通票', '全天不限时 · 含家长陪同', '', 9900, 16800, 0, 1),
(1000563, 263, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'https://aos-comment.amap.com/B0MBFM2JZA/comment/B6108DE1_9C5A_4A01_994C_779F7628A709_L0_001_2000_200_1772173413271_44453404.jpg', 5900, 9900, 1, 1),
(1000564, 263, '亲子全天通票', '全天不限时 · 含家长陪同', 'https://aos-comment.amap.com/B0MBFM2JZA/comment/B6108DE1_9C5A_4A01_994C_779F7628A709_L0_001_2000_200_1772173413271_44453404.jpg', 9900, 16800, 0, 1),
(1000565, 264, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'https://store.is.autonavi.com/showpic/c0ff9f5960cb3b9eef93380db449108c', 5900, 9900, 1, 1),
(1000566, 264, '亲子全天通票', '全天不限时 · 含家长陪同', 'https://store.is.autonavi.com/showpic/c0ff9f5960cb3b9eef93380db449108c', 9900, 16800, 0, 1),
(1000567, 265, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'http://store.is.autonavi.com/showpic/5656854ba3104425a5b5aac0', 5900, 9900, 1, 1),
(1000568, 265, '亲子全天通票', '全天不限时 · 含家长陪同', 'http://store.is.autonavi.com/showpic/5656854ba3104425a5b5aac0', 9900, 16800, 0, 1),
(1000569, 266, '亲子畅玩2小时', '海洋球+滑梯+沙池', '', 5900, 9900, 1, 1),
(1000570, 266, '亲子全天通票', '全天不限时 · 含家长陪同', '', 9900, 16800, 0, 1),
(1000571, 267, '亲子畅玩2小时', '海洋球+滑梯+沙池', 'http://store.is.autonavi.com/showpic/3b3516c7248ac5dd3ba670c4a2467779', 5900, 9900, 1, 1),
(1000572, 267, '亲子全天通票', '全天不限时 · 含家长陪同', 'http://store.is.autonavi.com/showpic/3b3516c7248ac5dd3ba670c4a2467779', 9900, 16800, 0, 1),
(1000573, 268, '亲子畅玩2小时', '海洋球+滑梯+沙池', '', 5900, 9900, 1, 1),
(1000574, 268, '亲子全天通票', '全天不限时 · 含家长陪同', '', 9900, 16800, 0, 1),
(1000575, 269, '亲子畅玩2小时', '海洋球+滑梯+沙池', '', 5900, 9900, 1, 1),
(1000576, 269, '亲子全天通票', '全天不限时 · 含家长陪同', '', 9900, 16800, 0, 1),
(1000577, 270, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://aos-comment.amap.com/B0LGM7F3VB/comment/content_media_external_file_6718_ss__1748145755439_10166626.jpg', 8900, 15800, 1, 1),
(1000578, 270, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://aos-comment.amap.com/B0LGM7F3VB/comment/content_media_external_file_6718_ss__1748145755439_10166626.jpg', 12900, 20800, 0, 1),
(1000579, 271, '精酿畅饮套餐', '精酿4杯 · 赠小食', '', 8900, 15800, 1, 1),
(1000580, 271, '双人微醺套餐', '鸡尾酒2杯+果盘', '', 12900, 20800, 0, 1),
(1000581, 272, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://store.is.autonavi.com/showpic/add41bd10c9e45ff752c16c27dfe7e85', 8900, 15800, 1, 1),
(1000582, 272, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://store.is.autonavi.com/showpic/add41bd10c9e45ff752c16c27dfe7e85', 12900, 20800, 0, 1),
(1000583, 273, '精酿畅饮套餐', '精酿4杯 · 赠小食', '', 8900, 15800, 1, 1),
(1000584, 273, '双人微醺套餐', '鸡尾酒2杯+果盘', '', 12900, 20800, 0, 1),
(1000585, 274, '精酿畅饮套餐', '精酿4杯 · 赠小食', '', 8900, 15800, 1, 1),
(1000586, 274, '双人微醺套餐', '鸡尾酒2杯+果盘', '', 12900, 20800, 0, 1),
(1000587, 275, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://store.is.autonavi.com/showpic/ce6ff83e745e5deae1cfc290b5f315ce', 8900, 15800, 1, 1),
(1000588, 275, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://store.is.autonavi.com/showpic/ce6ff83e745e5deae1cfc290b5f315ce', 12900, 20800, 0, 1),
(1000589, 276, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://store.is.autonavi.com/showpic/fbe8913e059f7d140000000972792498?type=pic', 8900, 15800, 1, 1),
(1000590, 276, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://store.is.autonavi.com/showpic/fbe8913e059f7d140000000972792498?type=pic', 12900, 20800, 0, 1),
(1000591, 277, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://store.is.autonavi.com/showpic/29ae44172151269bdf95ea1e2dd19d28', 8900, 15800, 1, 1),
(1000592, 277, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://store.is.autonavi.com/showpic/29ae44172151269bdf95ea1e2dd19d28', 12900, 20800, 0, 1),
(1000593, 278, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://store.is.autonavi.com/showpic/888f6eb8fcae1845e61af5a24f7d3df1', 8900, 15800, 1, 1),
(1000594, 278, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://store.is.autonavi.com/showpic/888f6eb8fcae1845e61af5a24f7d3df1', 12900, 20800, 0, 1),
(1000595, 279, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'http://store.is.autonavi.com/showpic/e62bbab3290a989526ed553612511dd2', 8900, 15800, 1, 1),
(1000596, 279, '双人微醺套餐', '鸡尾酒2杯+果盘', 'http://store.is.autonavi.com/showpic/e62bbab3290a989526ed553612511dd2', 12900, 20800, 0, 1),
(1000597, 280, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://aos-comment.amap.com/B0L1AUJFNP/comment/2a135302-c561-4a5d-9eeb-ab17ce9ded35.png', 8900, 15800, 1, 1),
(1000598, 280, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://aos-comment.amap.com/B0L1AUJFNP/comment/2a135302-c561-4a5d-9eeb-ab17ce9ded35.png', 12900, 20800, 0, 1),
(1000599, 281, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://store.is.autonavi.com/showpic/39c4dd21059cd37ee74396a52eadabbb', 8900, 15800, 1, 1),
(1000600, 281, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://store.is.autonavi.com/showpic/39c4dd21059cd37ee74396a52eadabbb', 12900, 20800, 0, 1),
(1000601, 282, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'http://store.is.autonavi.com/query_pic?id=st77d091b5-b1e2-4e76-be7b-85696a809271&user=search&operate=original', 8900, 15800, 1, 1),
(1000602, 282, '双人微醺套餐', '鸡尾酒2杯+果盘', 'http://store.is.autonavi.com/query_pic?id=st77d091b5-b1e2-4e76-be7b-85696a809271&user=search&operate=original', 12900, 20800, 0, 1),
(1000603, 283, '精酿畅饮套餐', '精酿4杯 · 赠小食', 'https://store.is.autonavi.com/showpic/c198266a2cc065d20000000731962692?type=pic', 8900, 15800, 1, 1),
(1000604, 283, '双人微醺套餐', '鸡尾酒2杯+果盘', 'https://store.is.autonavi.com/showpic/c198266a2cc065d20000000731962692?type=pic', 12900, 20800, 0, 1),
(1000605, 284, '精酿畅饮套餐', '精酿4杯 · 赠小食', '', 8900, 15800, 1, 1),
(1000606, 284, '双人微醺套餐', '鸡尾酒2杯+果盘', '', 12900, 20800, 0, 1),
(1000607, 285, '包场4小时', '含KTV+桌游 · 最多12人', 'https://store.is.autonavi.com/showpic/c85518898aa1dfd07fe8a100cc801339', 29900, 49900, 1, 1),
(1000608, 285, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'https://store.is.autonavi.com/showpic/c85518898aa1dfd07fe8a100cc801339', 49900, 79900, 0, 1),
(1000609, 286, '包场4小时', '含KTV+桌游 · 最多12人', '', 29900, 49900, 1, 1),
(1000610, 286, '包场8小时豪华套餐', '含KTV+台球+餐饮', '', 49900, 79900, 0, 1),
(1000611, 287, '包场4小时', '含KTV+桌游 · 最多12人', 'http://store.is.autonavi.com/showpic/e3bde239b3b978a09c000cd11276039e', 29900, 49900, 1, 1),
(1000612, 287, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'http://store.is.autonavi.com/showpic/e3bde239b3b978a09c000cd11276039e', 49900, 79900, 0, 1),
(1000613, 288, '包场4小时', '含KTV+桌游 · 最多12人', '', 29900, 49900, 1, 1),
(1000614, 288, '包场8小时豪华套餐', '含KTV+台球+餐饮', '', 49900, 79900, 0, 1),
(1000615, 289, '包场4小时', '含KTV+桌游 · 最多12人', '', 29900, 49900, 1, 1),
(1000616, 289, '包场8小时豪华套餐', '含KTV+台球+餐饮', '', 49900, 79900, 0, 1),
(1000617, 290, '包场4小时', '含KTV+桌游 · 最多12人', 'http://store.is.autonavi.com/showpic/b6fb25a6b47c77bb12f212265923f906', 29900, 49900, 1, 1),
(1000618, 290, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'http://store.is.autonavi.com/showpic/b6fb25a6b47c77bb12f212265923f906', 49900, 79900, 0, 1),
(1000619, 291, '包场4小时', '含KTV+桌游 · 最多12人', 'http://store.is.autonavi.com/showpic/7a9fd8e04fad48bed38038d01c065762', 29900, 49900, 1, 1),
(1000620, 291, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'http://store.is.autonavi.com/showpic/7a9fd8e04fad48bed38038d01c065762', 49900, 79900, 0, 1),
(1000621, 292, '包场4小时', '含KTV+桌游 · 最多12人', '', 29900, 49900, 1, 1),
(1000622, 292, '包场8小时豪华套餐', '含KTV+台球+餐饮', '', 49900, 79900, 0, 1),
(1000623, 293, '包场4小时', '含KTV+桌游 · 最多12人', 'https://aos-comment.amap.com/B0MBFM2JZA/comment/B6108DE1_9C5A_4A01_994C_779F7628A709_L0_001_2000_200_1772173413271_44453404.jpg', 29900, 49900, 1, 1),
(1000624, 293, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'https://aos-comment.amap.com/B0MBFM2JZA/comment/B6108DE1_9C5A_4A01_994C_779F7628A709_L0_001_2000_200_1772173413271_44453404.jpg', 49900, 79900, 0, 1),
(1000625, 294, '包场4小时', '含KTV+桌游 · 最多12人', 'https://store.is.autonavi.com/showpic/c0ff9f5960cb3b9eef93380db449108c', 29900, 49900, 1, 1),
(1000626, 294, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'https://store.is.autonavi.com/showpic/c0ff9f5960cb3b9eef93380db449108c', 49900, 79900, 0, 1),
(1000627, 295, '包场4小时', '含KTV+桌游 · 最多12人', 'http://store.is.autonavi.com/showpic/5656854ba3104425a5b5aac0', 29900, 49900, 1, 1),
(1000628, 295, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'http://store.is.autonavi.com/showpic/5656854ba3104425a5b5aac0', 49900, 79900, 0, 1),
(1000629, 296, '包场4小时', '含KTV+桌游 · 最多12人', '', 29900, 49900, 1, 1),
(1000630, 296, '包场8小时豪华套餐', '含KTV+台球+餐饮', '', 49900, 79900, 0, 1),
(1000631, 297, '包场4小时', '含KTV+桌游 · 最多12人', 'http://store.is.autonavi.com/showpic/3b3516c7248ac5dd3ba670c4a2467779', 29900, 49900, 1, 1),
(1000632, 297, '包场8小时豪华套餐', '含KTV+台球+餐饮', 'http://store.is.autonavi.com/showpic/3b3516c7248ac5dd3ba670c4a2467779', 49900, 79900, 0, 1),
(1000633, 298, '包场4小时', '含KTV+桌游 · 最多12人', '', 29900, 49900, 1, 1),
(1000634, 298, '包场8小时豪华套餐', '含KTV+台球+餐饮', '', 49900, 79900, 0, 1),
(1000635, 299, '包场4小时', '含KTV+桌游 · 最多12人', '', 29900, 49900, 1, 1),
(1000636, 299, '包场8小时豪华套餐', '含KTV+台球+餐饮', '', 49900, 79900, 0, 1),
(1000637, 300, '基础单色美甲', '任选单色 · 含护理', '', 3900, 7800, 1, 1),
(1000638, 300, '美甲+美睫套餐', '单色美甲+自然款美睫', '', 12900, 21800, 0, 1),
(1000639, 301, '基础单色美甲', '任选单色 · 含护理', 'http://store.is.autonavi.com/showpic/6f9c6e7b80b5efd0d5f2614eae97681d', 3900, 7800, 1, 1),
(1000640, 301, '美甲+美睫套餐', '单色美甲+自然款美睫', 'http://store.is.autonavi.com/showpic/6f9c6e7b80b5efd0d5f2614eae97681d', 12900, 21800, 0, 1),
(1000641, 302, '基础单色美甲', '任选单色 · 含护理', 'https://aos-comment.amap.com/B0JRXH6AJX/headerImg/45322192285cc8700831f8861643fac3_2048_2048_80.jpg', 3900, 7800, 1, 1),
(1000642, 302, '美甲+美睫套餐', '单色美甲+自然款美睫', 'https://aos-comment.amap.com/B0JRXH6AJX/headerImg/45322192285cc8700831f8861643fac3_2048_2048_80.jpg', 12900, 21800, 0, 1),
(1000643, 303, '基础单色美甲', '任选单色 · 含护理', '', 3900, 7800, 1, 1),
(1000644, 303, '美甲+美睫套餐', '单色美甲+自然款美睫', '', 12900, 21800, 0, 1),
(1000645, 304, '基础单色美甲', '任选单色 · 含护理', '', 3900, 7800, 1, 1),
(1000646, 304, '美甲+美睫套餐', '单色美甲+自然款美睫', '', 12900, 21800, 0, 1),
(1000647, 305, '基础单色美甲', '任选单色 · 含护理', '', 3900, 7800, 1, 1),
(1000648, 305, '美甲+美睫套餐', '单色美甲+自然款美睫', '', 12900, 21800, 0, 1),
(1000649, 306, '基础单色美甲', '任选单色 · 含护理', 'https://store.is.autonavi.com/showpic/e0918c7465ceb56030ae9111eb319887', 3900, 7800, 1, 1),
(1000650, 306, '美甲+美睫套餐', '单色美甲+自然款美睫', 'https://store.is.autonavi.com/showpic/e0918c7465ceb56030ae9111eb319887', 12900, 21800, 0, 1),
(1000651, 307, '基础单色美甲', '任选单色 · 含护理', 'https://store.is.autonavi.com/showpic/b8f1532cb7a9bebf477bff4ee6be00bf', 3900, 7800, 1, 1),
(1000652, 307, '美甲+美睫套餐', '单色美甲+自然款美睫', 'https://store.is.autonavi.com/showpic/b8f1532cb7a9bebf477bff4ee6be00bf', 12900, 21800, 0, 1),
(1000653, 308, '基础单色美甲', '任选单色 · 含护理', 'http://store.is.autonavi.com/showpic/6d423d7182af7de430bd024a0cc360f4', 3900, 7800, 1, 1),
(1000654, 308, '美甲+美睫套餐', '单色美甲+自然款美睫', 'http://store.is.autonavi.com/showpic/6d423d7182af7de430bd024a0cc360f4', 12900, 21800, 0, 1),
(1000655, 309, '基础单色美甲', '任选单色 · 含护理', 'https://store.is.autonavi.com/showpic/d26b63a36133eedb0e4774c3eda8b4ec', 3900, 7800, 1, 1),
(1000656, 309, '美甲+美睫套餐', '单色美甲+自然款美睫', 'https://store.is.autonavi.com/showpic/d26b63a36133eedb0e4774c3eda8b4ec', 12900, 21800, 0, 1),
(1000657, 310, '基础单色美甲', '任选单色 · 含护理', 'http://store.is.autonavi.com/showpic/fd07682a6eef652abb388e176c7f1417', 3900, 7800, 1, 1),
(1000658, 310, '美甲+美睫套餐', '单色美甲+自然款美睫', 'http://store.is.autonavi.com/showpic/fd07682a6eef652abb388e176c7f1417', 12900, 21800, 0, 1),
(1000659, 311, '基础单色美甲', '任选单色 · 含护理', 'http://store.is.autonavi.com/showpic/0b9386f09c5e937f9b38217d52e13ee9', 3900, 7800, 1, 1),
(1000660, 311, '美甲+美睫套餐', '单色美甲+自然款美睫', 'http://store.is.autonavi.com/showpic/0b9386f09c5e937f9b38217d52e13ee9', 12900, 21800, 0, 1),
(1000661, 312, '基础单色美甲', '任选单色 · 含护理', 'http://store.is.autonavi.com/showpic/1ae24521dc08edd9e7a0c68aab384c78', 3900, 7800, 1, 1),
(1000662, 312, '美甲+美睫套餐', '单色美甲+自然款美睫', 'http://store.is.autonavi.com/showpic/1ae24521dc08edd9e7a0c68aab384c78', 12900, 21800, 0, 1),
(1000663, 313, '基础单色美甲', '任选单色 · 含护理', '', 3900, 7800, 1, 1),
(1000664, 313, '美甲+美睫套餐', '单色美甲+自然款美睫', '', 12900, 21800, 0, 1),
(1000665, 314, '基础单色美甲', '任选单色 · 含护理', 'https://aos-comment.amap.com/B0K135UT9T/comment/B3A4205E_B3F6_4E50_8D8D_7105E187A625_L0_001_1080_144_1754966017461_92948055.jpg', 3900, 7800, 1, 1),
(1000666, 314, '美甲+美睫套餐', '单色美甲+自然款美睫', 'https://aos-comment.amap.com/B0K135UT9T/comment/B3A4205E_B3F6_4E50_8D8D_7105E187A625_L0_001_1080_144_1754966017461_92948055.jpg', 12900, 21800, 0, 1);

INSERT INTO `tb_seckill_voucher` (`voucher_id`, `stock`, `begin_time`, `end_time`) VALUES
(1000000, 28, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000003, 25, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000006, 40, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000009, 11, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000012, 29, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000015, 36, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000018, 44, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000021, 21, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000024, 40, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000027, 40, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000029, 15, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000031, 33, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000033, 16, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000035, 10, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000037, 18, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000040, 25, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000043, 44, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000046, 22, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000049, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000052, 12, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000055, 17, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000058, 31, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000061, 38, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000064, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000067, 46, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000070, 11, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000073, 34, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000076, 24, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000079, 26, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000082, 37, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000084, 11, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000086, 28, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000088, 38, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000090, 13, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000092, 21, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000094, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000096, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000098, 17, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000100, 42, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000102, 35, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000104, 20, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000106, 26, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000108, 33, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000110, 38, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000112, 39, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000114, 30, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000116, 47, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000118, 19, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000120, 35, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000122, 17, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000124, 29, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000126, 17, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000128, 48, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000130, 16, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000132, 29, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000134, 42, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000136, 19, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000138, 49, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000140, 44, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000142, 18, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000144, 20, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000146, 16, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000148, 42, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000150, 30, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000152, 44, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000154, 18, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000156, 31, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000158, 27, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000160, 41, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000162, 43, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000164, 15, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000166, 23, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000168, 12, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000170, 19, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000172, 44, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000174, 43, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000176, 49, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000178, 17, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000180, 37, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000182, 48, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000184, 47, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000186, 18, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000188, 28, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000190, 30, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000192, 27, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000194, 23, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000196, 49, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000198, 15, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000200, 41, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000202, 39, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000204, 30, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000206, 43, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000208, 42, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000210, 25, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000212, 42, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000214, 19, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000216, 46, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000218, 23, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000220, 15, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000222, 22, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000224, 34, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000226, 36, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000228, 47, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000230, 11, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000232, 29, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000234, 26, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000236, 10, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000238, 21, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000240, 25, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000242, 36, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000244, 20, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000246, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000248, 11, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000250, 46, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000252, 45, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000254, 22, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000256, 36, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000258, 38, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000260, 26, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000262, 37, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000264, 48, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000266, 45, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000268, 47, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000270, 11, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000272, 44, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000274, 31, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000276, 17, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000278, 49, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000280, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000282, 10, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000284, 27, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000286, 21, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000288, 49, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000290, 18, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000292, 29, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000294, 26, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000296, 10, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000298, 21, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000300, 25, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000302, 36, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000304, 20, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000306, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000308, 11, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000310, 46, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000312, 45, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000314, 22, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000316, 36, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000318, 38, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000320, 26, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000322, 39, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000324, 30, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000326, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000328, 16, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000330, 43, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000332, 26, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000334, 31, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000336, 44, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000338, 27, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000340, 28, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000342, 33, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000344, 33, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000346, 39, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000348, 39, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000350, 48, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000352, 16, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000355, 36, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000358, 17, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000361, 23, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000364, 39, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000367, 46, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000370, 13, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000373, 19, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000376, 11, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000379, 39, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000382, 29, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000385, 12, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000388, 37, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000391, 24, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000394, 47, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000397, 42, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000399, 47, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000401, 42, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000403, 16, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000405, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000407, 27, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000409, 29, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000411, 27, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000413, 27, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000415, 33, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000417, 20, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000419, 13, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000421, 10, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000423, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000425, 43, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000427, 16, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000429, 47, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000431, 15, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000433, 40, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000435, 24, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000437, 25, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000439, 18, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000441, 13, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000443, 20, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000445, 13, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000447, 17, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000449, 18, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000451, 45, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000453, 21, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000455, 17, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000457, 42, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000459, 16, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000461, 47, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000463, 13, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000465, 49, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000467, 16, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000469, 38, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000471, 12, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000473, 37, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000475, 24, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000477, 21, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000479, 39, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000481, 46, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000483, 33, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000485, 15, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000487, 35, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000489, 42, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000491, 37, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000493, 29, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000495, 41, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000497, 30, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000499, 23, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000501, 39, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000503, 44, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000505, 11, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000507, 49, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000509, 35, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000511, 20, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000513, 22, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000515, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000517, 16, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000519, 47, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000521, 15, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000523, 40, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000525, 24, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000527, 25, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000529, 18, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000531, 13, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000533, 35, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000535, 30, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000537, 37, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000539, 33, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000541, 36, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000543, 19, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000545, 36, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000547, 26, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000549, 30, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000551, 46, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000553, 49, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000555, 19, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000557, 40, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000559, 22, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000561, 22, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000563, 10, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000565, 49, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000567, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000569, 35, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000571, 35, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000573, 43, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000575, 14, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000577, 27, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000579, 13, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000581, 23, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000583, 27, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000585, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000587, 19, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000589, 30, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000591, 45, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000593, 27, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000595, 13, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000597, 11, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000599, 11, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000601, 42, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000603, 29, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000605, 16, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000607, 26, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000609, 30, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000611, 46, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000613, 49, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000615, 19, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000617, 40, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000619, 22, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000621, 22, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000623, 10, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000625, 49, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000627, 32, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000629, 35, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000631, 35, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000633, 43, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000635, 14, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000637, 27, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000639, 38, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000641, 30, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000643, 17, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000645, 36, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000647, 41, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000649, 43, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000651, 28, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000653, 44, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000655, 25, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000657, 20, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000659, 43, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000661, 15, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000663, 26, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1000665, 12, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY));
