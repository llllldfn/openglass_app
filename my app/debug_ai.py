#!/usr/bin/env python3
"""
AI API 连接测试脚本
用于诊断AI功能问题
"""

import requests
import json
import base64
import os
import sys

def test_ai_api():
    """测试AI API连接"""
    # 测试配置（读取环境变量，便于安全传入）
    base_url = os.environ.get("ARK_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3/")
    api_key = os.environ.get("ARK_API_KEY")
    # 模型/接入点可以通过 ARK_MODEL 指定，未提供则使用演示占位ID
    model = os.environ.get("ARK_MODEL", "gpt-4o")

    # 测试端点
    test_url = base_url.rstrip('/') + "/chat/completions"
    
    # 测试请求
    headers = {
        "Content-Type": "application/json"
    }
    
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    
    # 简单的文本请求
    payload = {
        "model": model,
        "messages": [
            {"role": "user", "content": "Hello, how are you?"}
        ],
        "max_tokens": 100,
        "temperature": 0.7
    }
    
    print(f"🔍 测试AI API连接...")
    print(f"URL: {test_url}")
    print(f"Headers: {headers}")
    print(f"Payload: {json.dumps(payload, indent=2)}")
    
    try:
        response = requests.post(
            test_url,
            headers=headers,
            json=payload,
            timeout=30
        )
        
        print(f"\n📡 响应状态码: {response.status_code}")
        print(f"响应头: {dict(response.headers)}")
        
        if response.status_code == 200:
            result = response.json()
            print(f"✅ 成功! AI响应: {result}")
        else:
            print(f"❌ 请求失败: {response.text}")
            
    except requests.exceptions.RequestException as e:
        print(f"❌ 网络请求异常: {e}")
    except json.JSONDecodeError as e:
        print(f"❌ JSON解析错误: {e}")
    except Exception as e:
        print(f"❌ 未知错误: {e}")

def test_network_connectivity():
    """测试网络连接"""
    print("\n🌐 测试网络连接...")
    
    test_urls = [
        "https://www.google.com",
        "https://ark.cn-beijing.volces.com",
        "https://api.openai.com"
    ]
    
    for url in test_urls:
        try:
            response = requests.get(url, timeout=10)
            print(f"✅ {url}: {response.status_code}")
        except Exception as e:
            print(f"❌ {url}: {e}")

if __name__ == "__main__":
    print("🚀 AI API 诊断工具")
    print("=" * 50)
    
    test_network_connectivity()
    test_ai_api()
    
    print("\n" + "=" * 50)
    print("诊断完成!")
