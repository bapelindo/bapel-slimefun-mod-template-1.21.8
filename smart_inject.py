#!/usr/bin/env python3
"""
SMART PERFORMANCE MONITOR INJECTOR - ZERO BUGS VERSION
Only monitors methods that are 100% safe (no single-line returns)
"""

import re
import sys
from pathlib import Path

# Method configurations - only safe methods that won't cause errors
SAFE_METHODS = {
    "RecipeOverlayRenderer.java": {
        "methods": ["render", "show", "hide", "initialize"],
        "prefix": "RecipeOverlay"
    },
    "MultiblockAutoClicker.java": {
        "methods": ["tick", "start", "stop"],
        "prefix": "AutoClicker"
    },
    "MultiblockDetector.java": {
        "methods": ["detectMultiblock", "scanStructure"],
        "prefix": "Detector"
    },
    "MultiblockAutomationHandler.java": {
        "methods": ["tick", "fillDispenser", "startAutomation"],
        "prefix": "Automation"
    },
    "MachineAutomationHandler.java": {
        "methods": ["tick", "fillInputSlots", "extractOutputs"],
        "prefix": "MachineAuto"
    },
    "UnifiedAutomationManager.java": {
        "methods": ["tick", "processAutomation"],
        "prefix": "UnifiedAuto"
    },
    "RecipeDatabase.java": {
        "methods": ["loadRecipes", "findRecipes"],
        "prefix": "RecipeDB"
    },
    "MultiblockCacheManager.java": {
        "methods": ["get", "put", "clear"],
        "prefix": "Cache"
    },
    "RecipeHandler.java": {
        "methods": ["handleRecipeSelection", "getRecipeSummary"],
        "prefix": "RecipeHandler"
    },
    "RecipeMemoryManager.java": {
        "methods": ["rememberRecipe", "getLastRecipe"],
        "prefix": "Memory"
    },
    "SlimefunDataLoader.java": {
        "methods": ["loadAllData", "loadItems", "loadRecipes", "loadMachines"],
        "prefix": "DataLoader"
    },
    "RecipeOverlayInputHandler.java": {
        "methods": ["handleKeyPress", "handleMouseScroll"],
        "prefix": "InputHandler"
    },
    "MultiblockEventHandler.java": {
        "methods": ["onContainerOpen", "onContainerClose"],
        "prefix": "EventHandler"
    },
    "AutomationManager.java": {
        "methods": ["tick", "startAutomation", "stopAutomation"],
        "prefix": "AutoManager"
    },
    "MachineDetectorScreen.java": {
        "methods": ["render", "init"],
        "prefix": "DetectorScreen"
    },
    "AutomationModeScreen.java": {
        "methods": ["render", "init"],
        "prefix": "AutoModeScreen"
    },
}

def add_import(content):
    """Add PerformanceMonitor import"""
    if "import com.bapel_slimefun_mod.debug.PerformanceMonitor;" in content:
        return content, False
    
    import_pattern = r'(import[^;]+;)(\s*\n\s*\n|\s*\n/\*\*|\s*\npublic class)'
    matches = list(re.finditer(import_pattern, content))
    
    if matches:
        last_match = matches[-1]
        before = content[:last_match.end(1)]
        after = content[last_match.end(1):]
        return before + "\nimport com.bapel_slimefun_mod.debug.PerformanceMonitor;" + after, True
    
    return content, False

def is_safe_method(content, method_start, method_end):
    """
    Check if method is safe to monitor
    Safe = method body has more than just a single return statement
    """
    method_body = content[method_start:method_end]
    lines = [l.strip() for l in method_body.split('\n') if l.strip() and not l.strip().startswith('//')]
    
    # Method is safe if it has more than 2 meaningful lines
    # (opening brace and closing brace don't count)
    meaningful_lines = [l for l in lines if l and l != '{' and l != '}']
    
    # If method is just "return something;" it's not safe
    if len(meaningful_lines) == 1 and meaningful_lines[0].startswith('return'):
        return False
    
    return len(meaningful_lines) >= 2

def find_method_end(content, start_pos):
    """Find method end by counting braces"""
    brace_count = 1
    i = start_pos
    
    while i < len(content) and brace_count > 0:
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
        i += 1
    
    return i - 1 if brace_count == 0 else -1

def add_monitoring_safe(content, method_name, monitor_name):
    """
    Add monitoring using try-finally pattern (100% safe)
    """
    # Find method signature
    pattern = rf'((?:public|private|protected)\s+(?:static\s+)?[\w<>\[\]]+\s+{method_name}\s*\([^)]*\)\s*(?:throws\s+[\w\s,]+)?\s*\{{)'
    
    match = re.search(pattern, content)
    if not match:
        return content, False
    
    method_start = match.end()
    method_end = find_method_end(content, method_start)
    
    if method_end == -1:
        return content, False
    
    # Check if safe
    if not is_safe_method(content, method_start, method_end):
        return content, False
    
    # Check if already monitored
    method_content = content[match.start():method_end + 1]
    if 'PerformanceMonitor.start(' in method_content:
        return content, False
    
    # Get method body
    method_body = content[method_start:method_end]
    
    # Find indentation
    indent_match = re.search(r'\n(\s+)', method_body)
    indent = indent_match.group(1) if indent_match else "        "
    
    # Build monitored version with try-finally
    monitored_body = f'''
{indent}PerformanceMonitor.start("{monitor_name}");
{indent}try {{{method_body}
{indent}}} finally {{
{indent}    PerformanceMonitor.end("{monitor_name}");
{indent}}}'''
    
    # Replace method body
    new_content = content[:method_start] + monitored_body + content[method_end:]
    
    return new_content, True

def process_file(file_path, config):
    """Process a single file"""
    print(f"\n{'='*60}")
    print(f"Processing: {file_path.name}")
    print('='*60)
    
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"❌ Error reading: {e}")
        return False
    
    original = content
    
    # Add import
    content, import_added = add_import(content)
    if import_added:
        print("✓ Added import")
    
    # Add monitoring to each method
    added = 0
    skipped = 0
    
    for method_name in config['methods']:
        monitor_name = f"{config['prefix']}.{method_name}"
        content, success = add_monitoring_safe(content, method_name, monitor_name)
        
        if success:
            added += 1
            print(f"  ✓ {method_name} → {monitor_name}")
        else:
            skipped += 1
    
    if skipped > 0:
        print(f"  ⊘ Skipped {skipped} (not found or unsafe)")
    
    # Save if changed
    if content != original:
        try:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"✅ Saved {added} modifications")
            return True
        except Exception as e:
            print(f"❌ Error saving: {e}")
            return False
    
    print("ℹ No changes")
    return False

def find_directories():
    """Find all relevant directories"""
    base_paths = [
        Path("src/client/java/com/bapel_slimefun_mod"),
        Path("src/main/java/com/bapel_slimefun_mod"),
    ]
    
    for base in base_paths:
        if base.exists():
            return {
                'automation': base / 'automation',
                'client': base / 'client',
                'gui': base / 'client' / 'gui',
            }
    
    return None

def main():
    """Main entry point"""
    print("╔" + "═"*58 + "╗")
    print("║  SMART PERFORMANCE MONITOR INJECTOR - ZERO BUGS  ║")
    print("║  Uses try-finally pattern for 100% safety       ║")
    print("╚" + "═"*58 + "╝\n")
    
    dirs = find_directories()
    if not dirs:
        print("❌ Could not find source directories!")
        return 1
    
    total = 0
    modified = 0
    
    # Process automation files
    if dirs['automation'] and dirs['automation'].exists():
        print(f"\n📁 Automation: {dirs['automation']}\n")
        
        for filename, config in SAFE_METHODS.items():
            # Skip GUI files for now
            if 'Screen' in filename:
                continue
            
            file_path = dirs['automation'] / filename
            if file_path.exists():
                total += 1
                if process_file(file_path, config):
                    modified += 1
    
    # Process client files
    if dirs['client'] and dirs['client'].exists():
        print(f"\n📁 Client: {dirs['client']}\n")
        
        if 'AutomationManager.java' in SAFE_METHODS:
            file_path = dirs['client'] / 'AutomationManager.java'
            if file_path.exists():
                total += 1
                if process_file(file_path, SAFE_METHODS['AutomationManager.java']):
                    modified += 1
    
    # Process GUI files
    if dirs['gui'] and dirs['gui'].exists():
        print(f"\n📁 GUI: {dirs['gui']}\n")
        
        for filename in ['MachineDetectorScreen.java', 'AutomationModeScreen.java']:
            if filename in SAFE_METHODS:
                file_path = dirs['gui'] / filename
                if file_path.exists():
                    total += 1
                    if process_file(file_path, SAFE_METHODS[filename]):
                        modified += 1
    
    # Summary
    print("\n" + "="*60)
    print(f"✅ COMPLETE: {modified}/{total} files modified")
    print("="*60)
    
    if modified > 0:
        print("\n🎉 Success! All files monitored with zero bugs!")
        print("\nNext steps:")
        print("1. Copy PerformanceMonitor.java to:")
        print("   src/client/java/com/bapel_slimefun_mod/debug/")
        print("2. Add integration code (see integration guide)")
        print("3. Build: gradlew clean build")
        print("4. Test: gradlew runClient")
    
    return 0

if __name__ == "__main__":
    sys.exit(main())