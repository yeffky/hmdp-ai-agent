#!/usr/bin/env python
# 批量生成：读 manifest.tsv（out_relpath\tprompt），逐张生成到 out_root
# 用法: python batch.py manifest.tsv /home/zjk/sd-gen/out [--width 768 --height 768 --steps 25]
import argparse
import os
import sys
from gen import gen


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('manifest', help='TSV: out_relpath<TAB>prompt')
    ap.add_argument('out_root', help='输出根目录')
    ap.add_argument('--width', type=int, default=768)
    ap.add_argument('--height', type=int, default=768)
    ap.add_argument('--steps', type=int, default=25)
    ap.add_argument('--start', type=int, default=0)
    args = ap.parse_args()

    with open(args.manifest, encoding='utf-8') as f:
        lines = [l.rstrip('\n').split('\t', 1) for l in f if l.strip()]

    total = len(lines)
    ok = 0
    for i, (rel, prompt) in enumerate(lines):
        if i < args.start:
            continue
        out = os.path.join(args.out_root, rel)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        if os.path.exists(out) and os.path.getsize(out) > 1000:
            print(f'skip {i+1}/{total} {rel}')
            ok += 1
            continue
        print(f'gen {i+1}/{total} {rel}', flush=True)
        try:
            gen(prompt, out, width=args.width, height=args.height, seed=i, steps=args.steps)
            ok += 1
        except Exception as e:
            print(f'FAIL {rel}: {e}', flush=True)
    print(f'完成：成功 {ok}/{total}')


if __name__ == '__main__':
    main()
