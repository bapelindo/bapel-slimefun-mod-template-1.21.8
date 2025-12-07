#!/usr/bin/env python3
"""
CLEANUP SCRIPT - Remove all old monitoring code
Run this FIRST before installing new monitoring system
"""

import re
import sys
from pathlib import Path

def clean_file(file_path):
    """Remove all PerformanceMonitor code from a file"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception:
        return False
    
    if 'PerformanceMonitor' not in content:
        return False
    
    original = content
    
    # Remove import
    content = re.sub(r'import com\.bapel_slimefun_mod\.debug\.PerformanceMonitor;\s*\n', '', content)
    
    # Remove start() calls
    content = re.sub(r'\s*PerformanceMonitor\.start\([^)]+\);\s*\n?', '', content)
    
    # Remove end() calls
    content = re.sub(r'\s*PerformanceMonitor\.end\([^)]+\);\s*\n?', '', content)
    
    # Remove try-finally blocks with only PerformanceMonitor
    content = re.sub(r'\s*try\s*\{\s*\n?', '', content)
    content = re.sub(r'\s*\}\s*finally\s*\{\s*\n?\s*\}\s*\n?', '', content)
    
    if content != original:
        try:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            return True
        except Exception:
            return False
    
    return False

def main():
    """Main entry point"""
    print("="*60)
    print("  CLEANUP: Removing Old Monitoring Code")
    print("="*60)
    
    # Find Java source directories
    java_dirs = [
        Path("src/client/java"),
        Path("src/main/java"),
    ]
    
    cleaned = 0
    total = 0
    
    for java_dir in java_dirs:
        if not java_dir.exists():
            continue
        
        print(f"\nScanning: {java_dir}")
        
        for java_file in java_dir.rglob("*.java"):
            total += 1
            if clean_file(java_file):
                cleaned += 1
                print(f"  ✓ Cleaned: {java_file.name}")
    
    # Remove debug folder
    debug_dirs = [
        Path("src/client/java/com/bapel_slimefun_mod/debug"),
        Path("src/main/java/com/bapel_slimefun_mod/debug"),
    ]
    
    for debug_dir in debug_dirs:
        if debug_dir.exists():
            import shutil
            try:
                shutil.rmtree(debug_dir)
                print(f"\n✓ Removed: {debug_dir}")
            except Exception:
                pass
    
    print("\n" + "="*60)
    print(f"✅ Complete: Cleaned {cleaned} file(s)")
    print("="*60)
    
    if cleaned > 0 or any(d.exists() for d in debug_dirs):
        print("\n✓ Old monitoring code removed!")
        print("  Now ready for fresh installation")
        print("\n  Next: Run smart_inject.py")
    else:
        print("\n✓ No old monitoring code found")
        print("  Project is clean!")
    
    return 0

if __name__ == "__main__":
    sys.exit(main())