#!/usr/bin/env python
# ComfyUI 文生图封装：提交工作流 → 轮询 → 保存图片
# 用法: python gen.py --prompt "..." --out /path/to/out.png [--width 512 --height 512 --seed 42]
import argparse
import json
import sys
import time
import urllib.parse
import urllib.request
import uuid

SERVER = 'http://127.0.0.1:8188'
CKPT = 'Realistic_Vision_V5.1_fp16-no-ema.safetensors'

NEG = 'lowres, bad anatomy, bad hands, text, watermark, extra fingers, deformed, blurry, jpeg artifacts, worst quality, low quality, normal quality'


def http_json(path, method='GET', payload=None):
    url = SERVER + path
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    if payload is not None:
        req.add_header('Content-Type', 'application/json')
    with urllib.request.urlopen(req, timeout=600) as resp:
        return json.loads(resp.read().decode())


def build_workflow(prompt, width, height, seed, steps=20, prefix='gen'):
    wf = {
        '3': {'class_type': 'KSampler', 'inputs': {
            'seed': seed, 'steps': steps, 'cfg': 7, 'sampler_name': 'euler',
            'scheduler': 'normal', 'denoise': 1,
            'model': ['4', 0], 'positive': ['6', 0], 'negative': ['7', 0], 'latent_image': ['5', 0]}},
        '4': {'class_type': 'CheckpointLoaderSimple', 'inputs': {'ckpt_name': CKPT}},
        '5': {'class_type': 'EmptyLatentImage', 'inputs': {'width': width, 'height': height, 'batch_size': 1}},
        '6': {'class_type': 'CLIPTextEncode', 'inputs': {'text': prompt, 'clip': ['4', 1]}},
        '7': {'class_type': 'CLIPTextEncode', 'inputs': {'text': NEG, 'clip': ['4', 1]}},
        '8': {'class_type': 'VAEDecode', 'inputs': {'samples': ['3', 0], 'vae': ['4', 2]}},
        '9': {'class_type': 'SaveImage', 'inputs': {'filename_prefix': prefix, 'images': ['8', 0]}},
    }
    return wf


def gen(prompt, out_path, width=512, height=512, seed=None, steps=20, prefix='gen'):
    seed = seed if seed is not None else int(time.time() * 1000) % 2**31
    wf = build_workflow(prompt, width, height, seed, steps, prefix)
    client = uuid.uuid4().hex
    resp = http_json('/prompt', 'POST', {'prompt': wf, 'client_id': client})
    prompt_id = resp.get('prompt_id')
    if not prompt_id:
        raise RuntimeError('提交失败: ' + json.dumps(resp, ensure_ascii=False))
    # 轮询
    for _ in range(6000):
        time.sleep(1)
        h = http_json('/history/' + prompt_id)
        if prompt_id in h:
            outputs = h[prompt_id].get('outputs', {})
            for node_id, out in outputs.items():
                if 'images' in out and out['images']:
                    img = out['images'][0]
                    fn = img['filename']
                    sub = img.get('subfolder', '')
                    url = f'{SERVER}/view?filename={urllib.parse.quote(fn)}&subfolder={urllib.parse.quote(sub)}&type={img.get("type","output")}'
                    data = urllib.request.urlopen(url, timeout=120).read()
                    with open(out_path, 'wb') as f:
                        f.write(data)
                    return out_path
            # 有 outputs 但没有 images（可能出错）
            raise RuntimeError('生成完成但无图片输出')
        status = h.get(prompt_id, {}).get('status', {})
        if status.get('status_str') == 'error':
            raise RuntimeError('生成出错: ' + json.dumps(status, ensure_ascii=False))
    raise RuntimeError('生成超时')


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--prompt', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--width', type=int, default=512)
    ap.add_argument('--height', type=int, default=512)
    ap.add_argument('--seed', type=int, default=None)
    ap.add_argument('--steps', type=int, default=20)
    ap.add_argument('--prefix', default='gen')
    args = ap.parse_args()
    out = gen(args.prompt, args.out, args.width, args.height, args.seed, args.steps, args.prefix)
    print('OK', out)
