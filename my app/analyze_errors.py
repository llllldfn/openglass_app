import json
import sys
from collections import defaultdict

def analyze_log_errors(json_file_path):
    """分析日志文件中的错误信息"""
    try:
        with open(json_file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        print("=== 日志错误分析报告 ===\n")
        
        # 统计信息
        total_messages = len(data.get('logcatMessages', []))
        error_messages = []
        error_by_tag = defaultdict(list)
        error_by_process = defaultdict(list)
        
        # 提取错误信息
        for msg in data.get('logcatMessages', []):
            if msg.get('header', {}).get('logLevel') == 'ERROR':
                error_info = {
                    'timestamp': msg.get('header', {}).get('timestamp', {}),
                    'tag': msg.get('header', {}).get('tag', ''),
                    'process': msg.get('header', {}).get('processName', ''),
                    'pid': msg.get('header', {}).get('pid', ''),
                    'message': msg.get('message', ''),
                    'applicationId': msg.get('header', {}).get('applicationId', '')
                }
                error_messages.append(error_info)
                
                # 按标签分组
                error_by_tag[error_info['tag']].append(error_info)
                # 按进程分组
                error_by_process[error_info['process']].append(error_info)
        
        print(f"总日志消息数: {total_messages}")
        print(f"错误消息数: {len(error_messages)}")
        print(f"错误率: {len(error_messages)/total_messages*100:.2f}%\n")
        
        # 显示错误统计
        print("=== 按标签分组的错误统计 ===")
        for tag, errors in sorted(error_by_tag.items(), key=lambda x: len(x[1]), reverse=True):
            print(f"{tag}: {len(errors)} 个错误")
        
        print("\n=== 按进程分组的错误统计 ===")
        for process, errors in sorted(error_by_process.items(), key=lambda x: len(x[1]), reverse=True):
            print(f"{process}: {len(errors)} 个错误")
        
        print("\n=== 前20个错误详情 ===")
        for i, error in enumerate(error_messages[:20]):
            timestamp = error['timestamp']
            time_str = f"{timestamp.get('seconds', '')}.{timestamp.get('nanos', '')}" if timestamp else 'N/A'
            print(f"{i+1}. [{time_str}] {error['tag']} ({error['process']}:{error['pid']})")
            print(f"   消息: {error['message']}")
            print()
        
        # 查找关键错误模式
        print("=== 关键错误模式分析 ===")
        critical_errors = []
        for error in error_messages:
            msg = error['message'].lower()
            if any(keyword in msg for keyword in ['fail', 'error', 'exception', 'crash', 'timeout', 'null']):
                critical_errors.append(error)
        
        print(f"发现 {len(critical_errors)} 个关键错误:")
        for i, error in enumerate(critical_errors[:10]):
            timestamp = error['timestamp']
            time_str = f"{timestamp.get('seconds', '')}.{timestamp.get('nanos', '')}" if timestamp else 'N/A'
            print(f"{i+1}. [{time_str}] {error['tag']} - {error['message']}")
        
        return error_messages
        
    except Exception as e:
        print(f"分析文件时出错: {e}")
        return []

if __name__ == "__main__":
    json_file = "Untitled-1.json"
    errors = analyze_log_errors(json_file)


