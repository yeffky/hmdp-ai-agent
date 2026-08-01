#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
秒杀并发测试 —— Redis 预置脚本
=================================
作用:
  1. 从 application.yaml 读取 Redis 连接(host/port/password), 不硬编码密钥
  2. 设置秒杀库存  seckill:stock:{voucherId}
  3. 清空已购集合 seckill:order:{voucherId}  (保证干净起点)
  4. 为 N 个测试手机号批量写入登录验证码 login:code:{phone} = 123456
  5. 生成与预置码对应的 phones.csv (供 JMeter CSV Data Set 使用)

用法:
  python load-test/seed_redis.py [voucherId] [stock] [phoneCount]
  示例:
  python load-test/seed_redis.py 666 100 500
"""
import re
import sys
import os
import redis


def load_redis_conf(project_root):
    """从 application.yaml 的 spring.redis 段正则提取 host/port/password。"""
    path = os.path.join(project_root, 'hm-dianping', 'src', 'main', 'resources', 'application.yaml')
    with open(path, encoding='utf-8') as f:
        lines = f.readlines()

    start = None
    for i, line in enumerate(lines):
        if re.match(r'^\s+redis:\s*$', line):
            start = i
            break
    if start is None:
        raise SystemExit('未在 application.yaml 找到 spring.redis 配置')

    conf = {}
    base_indent = None
    for line in lines[start + 1:]:
        m = re.match(r'^(\s+)(\w+):\s*([^#]*)', line)
        if not m:
            continue
        indent = m.group(1)
        if base_indent is None:
            base_indent = indent
        elif len(indent) < len(base_indent):
            break
        key = m.group(2)
        val = m.group(3).strip().strip('"\'')
        if key in ('host', 'port', 'password'):
            conf[key] = val
    if 'host' not in conf:
        raise SystemExit('redis 配置解析失败')
    return conf


def main():
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    voucher_id = sys.argv[1] if len(sys.argv) > 1 else '666'
    stock = int(sys.argv[2]) if len(sys.argv) > 2 else 100
    phone_count = int(sys.argv[3]) if len(sys.argv) > 3 else 500
    code = '123456'
    base_phone = 13800000000

    conf = load_redis_conf(project_root)
    r = redis.Redis(host=conf['host'], port=int(conf['port']),
                    password=conf.get('password'), decode_responses=True,
                    socket_timeout=10)

    # 1. 设置秒杀库存
    stock_key = f'seckill:stock:{voucher_id}'
    order_key = f'seckill:order:{voucher_id}'
    r.set(stock_key, stock)
    print(f'[1/4] 设置库存 {stock_key} = {stock}')

    # 2. 清空已购集合
    r.delete(order_key)
    print(f'[2/4] 清空已购集合 {order_key}')

    # 3. 批量写入登录验证码 (TTL 与代码 LOGIN_CODE_TTL 对齐, 放宽到 5min 避免测试中过期)
    pipeline = r.pipeline(transaction=False)
    for i in range(1, phone_count + 1):
        pipeline.set(f'login:code:{base_phone + i}', code, ex=300)
    pipeline.execute()
    print(f'[3/4] 写入 {phone_count} 个登录验证码 login:code:{{phone}} = {code} (TTL 300s)')

    # 4. 生成 phones.csv (与预置验证码一一对应)
    csv_path = os.path.join(project_root, 'load-test', 'phones.csv')
    with open(csv_path, 'w', encoding='utf-8', newline='\n') as f:
        for i in range(1, phone_count + 1):
            f.write(f'{base_phone + i}\n')
    print(f'[4/4] 生成 {csv_path} 共 {phone_count} 行')

    print('\n预置完成, 可启动 JMeter 执行 seckill-load-test.jmx')
    print('')
    print('重要: 还需重置 MySQL 库存(createVoucherOrder 落单时校验的是 MySQL 而非 Redis 库存):')
    print(f"  UPDATE tb_seckill_voucher SET stock = {stock} WHERE voucher_id = {voucher_id};")


if __name__ == '__main__':
    main()
