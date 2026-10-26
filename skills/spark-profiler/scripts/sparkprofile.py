#!/usr/bin/env python3
"""sparkprofile — 无依赖的 spark .sparkprofile 解析器 / 分析器。

用法示例：
  python sparkprofile.py info   profile.sparkprofile
  python sparkprofile.py report profile.sparkprofile -o report.md
  python sparkprofile.py tree   profile.sparkprofile --thread "Server thread" --depth 12
  python sparkprofile.py top    profile.sparkprofile --self --limit 40
  python sparkprofile.py json   profile.sparkprofile -o profile.json --slim
  python sparkprofile.py pkg    profile.sparkprofile --limit 30
  python sparkprofile.py diff   a.sparkprofile b.sparkprofile

设计要点：
- .sparkprofile 是 protobuf 编码的 SamplerData（部分版本再套 gzip），本脚本自带 wire 解码器，
  不依赖 protobuf runtime / protoc，也不依赖 Minecraft / Java。
- 时间单位：每个节点的 times[] 是“采样次数”数组（double），乘以 metadata.interval(ms) 即毫秒。
  多时间窗时 times 长度 = 窗口数，逐列对应 metadata.time_windows。
"""

from __future__ import annotations

import argparse
import gzip
import io
import json
import os
import struct
import sys
from collections import defaultdict

# --------------------------------------------------------------------------------------
# protobuf schema（来源：spark-common/src/main/proto/spark/spark_sampler.proto + spark.proto）
# 字段表：number -> (name, kind, repeated)
#   kind: 'str' | 'int' | 'double' | 'bool' | 'bytes' | ('msg', MessageName) | ('enum', EnumName)
#         | ('map', keykind, valkind)
# --------------------------------------------------------------------------------------

def _m(name):
    return ('msg', name)


S = {}

S['SamplerData'] = {
    1: ('metadata', _m('SamplerMetadata'), False),
    2: ('threads', _m('ThreadNode'), True),
    3: ('class_sources', ('map', 'str', 'str'), False),
    4: ('method_sources', ('map', 'str', 'str'), False),
    5: ('line_sources', ('map', 'str', 'str'), False),
    6: ('time_windows', 'int', True),
    7: ('time_window_statistics', ('map', 'int', _m('WindowStatistics')), False),
    8: ('channel_info', _m('SocketChannelInfo'), False),
}

S['SamplerMetadata'] = {
    1: ('creator', _m('CommandSenderMetadata'), False),
    2: ('start_time', 'int', False),
    3: ('interval', 'int', False),
    4: ('thread_dumper', _m('ThreadDumper'), False),
    5: ('data_aggregator', _m('DataAggregator'), False),
    6: ('comment', 'str', False),
    7: ('platform_metadata', _m('PlatformMetadata'), False),
    8: ('platform_statistics', _m('PlatformStatistics'), False),
    9: ('system_statistics', _m('SystemStatistics'), False),
    10: ('server_configurations', ('map', 'str', 'str'), False),
    11: ('end_time', 'int', False),
    12: ('number_of_ticks', 'int', False),
    13: ('sources', ('map', 'str', _m('PluginOrModMetadata')), False),
    14: ('extra_platform_metadata', ('map', 'str', 'str'), False),
    15: ('sampler_mode', ('enum', 'SamplerMode'), False),
    16: ('sampler_engine', ('enum', 'SamplerEngine'), False),
    17: ('sampler_engine_version', 'str', False),
    18: ('metrics', _m('Metrics'), False),
}

S['ThreadDumper'] = {
    1: ('type', ('enum', 'ThreadDumperType'), False),
    2: ('ids', 'int', True),
    3: ('patterns', 'str', True),
}

S['DataAggregator'] = {
    1: ('type', ('enum', 'AggregatorType'), False),
    2: ('thread_grouper', ('enum', 'ThreadGrouper'), False),
    3: ('tick_length_threshold', 'int', False),
    4: ('number_of_included_ticks', 'int', False),
}

S['ThreadNode'] = {
    1: ('name', 'str', False),
    3: ('children', _m('StackTraceNode'), True),
    4: ('times', 'double', True),
    5: ('children_refs', 'int', True),
}

S['StackTraceNode'] = {
    3: ('class_name', 'str', False),
    4: ('method_name', 'str', False),
    5: ('parent_line_number', 'int', False),
    6: ('line_number', 'int', False),
    7: ('method_desc', 'str', False),
    8: ('times', 'double', True),
    9: ('children_refs', 'int', True),
}

S['SocketChannelInfo'] = {1: ('channel_id', 'str', False), 2: ('public_key', 'bytes', False)}

S['PlatformMetadata'] = {
    1: ('type', ('enum', 'PlatformType'), False),
    2: ('name', 'str', False),
    3: ('version', 'str', False),
    4: ('minecraft_version', 'str', False),
    7: ('spark_data_version', 'int', False),
    8: ('brand', 'str', False),
    9: ('spark_version', 'str', False),
}

S['CommandSenderMetadata'] = {
    1: ('type', ('enum', 'SenderType'), False),
    2: ('name', 'str', False),
    3: ('unique_id', 'str', False),
}

S['PluginOrModMetadata'] = {
    1: ('name', 'str', False),
    2: ('version', 'str', False),
    3: ('author', 'str', False),
    4: ('description', 'str', False),
    5: ('builtin', 'bool', False),
}

S['PlatformStatistics'] = {
    1: ('memory', _m('PSMemory'), False),
    2: ('gc', ('map', 'str', _m('PSGc')), False),
    3: ('uptime', 'int', False),
    4: ('tps', _m('PSTps'), False),
    5: ('mspt', _m('PSMspt'), False),
    6: ('ping', _m('PSPing'), False),
    7: ('player_count', 'int', False),
    8: ('world', _m('WorldStatistics'), False),
    9: ('online_mode', ('enum', 'OnlineMode'), False),
}
S['PSMemory'] = {
    1: ('heap', _m('MemoryUsage'), False),
    2: ('non_heap', _m('MemoryUsage'), False),
    3: ('pools', _m('PSMemoryPool'), True),
    4: ('alloc_bps_last1m', _m('RollingAverageValues'), False),
    5: ('alloc_bps_last5m', _m('RollingAverageValues'), False),
    6: ('alloc_bps_last15m', _m('RollingAverageValues'), False),
}
S['PSMemoryPool'] = {
    1: ('name', 'str', False),
    2: ('usage', _m('MemoryUsage'), False),
    3: ('collection_usage', _m('MemoryUsage'), False),
}
S['PSGc'] = {
    1: ('total', 'int', False),
    2: ('avg_time', 'double', False),
    3: ('avg_frequency', 'double', False),
}
S['PSTps'] = {
    1: ('last1m', 'double', False),
    2: ('last5m', 'double', False),
    3: ('last15m', 'double', False),
    4: ('game_target_tps', 'int', False),
}
S['PSMspt'] = {
    1: ('last1m', _m('RollingAverageValues'), False),
    2: ('last5m', _m('RollingAverageValues'), False),
    3: ('game_max_ideal_mspt', 'int', False),
}
S['PSPing'] = {1: ('last15m', _m('RollingAverageValues'), False)}

S['RollingAverageValues'] = {
    1: ('mean', 'double', False),
    2: ('max', 'double', False),
    3: ('min', 'double', False),
    4: ('median', 'double', False),
    5: ('percentile95', 'double', False),
}
S['MemoryUsage'] = {
    1: ('used', 'int', False),
    2: ('committed', 'int', False),
    3: ('init', 'int', False),
    4: ('max', 'int', False),
}
S['WindowStatistics'] = {
    1: ('ticks', 'int', False),
    2: ('cpu_process', 'double', False),
    3: ('cpu_system', 'double', False),
    4: ('tps', 'double', False),
    5: ('mspt_median', 'double', False),
    6: ('mspt_max', 'double', False),
    7: ('players', 'int', False),
    8: ('entities', 'int', False),
    9: ('tile_entities', 'int', False),
    10: ('chunks', 'int', False),
    11: ('start_time', 'int', False),
    12: ('end_time', 'int', False),
    13: ('duration', 'int', False),
}

S['WorldStatistics'] = {
    1: ('total_entities', 'int', False),
    2: ('entity_counts', ('map', 'str', 'int'), False),
    3: ('worlds', _m('World'), True),
    4: ('game_rules', _m('GameRule'), True),
    5: ('data_packs', _m('DataPack'), True),
}
S['World'] = {
    1: ('name', 'str', False),
    2: ('total_entities', 'int', False),
    3: ('regions', _m('Region'), True),
}
S['Region'] = {1: ('total_entities', 'int', False), 2: ('chunks', _m('Chunk'), True)}
S['Chunk'] = {
    1: ('x', 'int', False),
    2: ('z', 'int', False),
    3: ('total_entities', 'int', False),
    4: ('entity_counts', ('map', 'str', 'int'), False),
}
S['GameRule'] = {
    1: ('name', 'str', False),
    2: ('default_value', 'str', False),
    3: ('world_values', ('map', 'str', 'str'), False),
}
S['DataPack'] = {
    1: ('name', 'str', False),
    2: ('description', 'str', False),
    3: ('source', 'str', False),
    4: ('builtin', 'bool', False),
}

S['SystemStatistics'] = {
    1: ('cpu', _m('SysCpu'), False),
    2: ('memory', _m('SysMemory'), False),
    3: ('gc', ('map', 'str', _m('PSGc')), False),
    4: ('disk', _m('SysDisk'), False),
    5: ('os', _m('SysOs'), False),
    6: ('java', _m('SysJava'), False),
    7: ('uptime', 'int', False),
    8: ('net', ('map', 'str', _m('NetInterface')), False),
    9: ('jvm', _m('SysJvm'), False),
}
S['SysCpu'] = {
    1: ('threads', 'int', False),
    2: ('process_usage', _m('CpuUsage'), False),
    3: ('system_usage', _m('CpuUsage'), False),
    4: ('model_name', 'str', False),
}
S['CpuUsage'] = {1: ('last1m', 'double', False), 2: ('last15m', 'double', False)}
S['SysMemory'] = {1: ('physical', _m('MemPool'), False), 2: ('swap', _m('MemPool'), False)}
S['MemPool'] = {1: ('used', 'int', False), 2: ('total', 'int', False)}
S['SysDisk'] = {1: ('used', 'int', False), 2: ('total', 'int', False)}
S['SysOs'] = {1: ('arch', 'str', False), 2: ('name', 'str', False), 3: ('version', 'str', False)}
S['SysJava'] = {
    1: ('vendor', 'str', False),
    2: ('version', 'str', False),
    3: ('vendor_version', 'str', False),
    4: ('vm_args', 'str', False),
}
S['SysJvm'] = {1: ('name', 'str', False), 2: ('vendor', 'str', False), 3: ('version', 'str', False)}
S['NetInterface'] = {
    1: ('rx_bytes_per_second', _m('RollingAverageValues'), False),
    2: ('tx_bytes_per_second', _m('RollingAverageValues'), False),
    3: ('rx_packets_per_second', _m('RollingAverageValues'), False),
    4: ('tx_packets_per_second', _m('RollingAverageValues'), False),
}

S['Metrics'] = {
    1: ('tps', _m('DoubleMetricSeries'), False),
    2: ('tick_duration', _m('AveragesMetricSeries'), False),
    3: ('cpu_usage_process', _m('DoubleMetricSeries'), False),
    4: ('cpu_usage_system', _m('DoubleMetricSeries'), False),
    5: ('memory_usage_heap', _m('MemoryUsageMetricSeries'), False),
    6: ('memory_usage_non_heap', _m('MemoryUsageMetricSeries'), False),
    7: ('memory_allocation', _m('DoubleMetricSeries'), False),
    8: ('world_info', _m('WorldInfoMetricSeries'), False),
    9: ('player_ping', _m('AveragesMetricSeries'), False),
}
S['DoubleMetricSeries'] = {
    1: ('start_timestamp_ms', 'int', False),
    2: ('timestamp_deltas_ms', 'int', True),
    3: ('values', 'double', True),
}
S['AveragesMetricSeries'] = {
    1: ('start_timestamp_ms', 'int', False),
    2: ('timestamp_deltas_ms', 'int', True),
    3: ('values', _m('RollingAverageValues'), True),
}
S['MemoryUsageMetricSeries'] = {
    1: ('start_timestamp_ms', 'int', False),
    2: ('timestamp_deltas_ms', 'int', True),
    3: ('values', _m('MemoryUsage'), True),
}
S['WorldInfoMetricSeries'] = {
    1: ('start_timestamp_ms', 'int', False),
    2: ('timestamp_deltas_ms', 'int', True),
    3: ('values', _m('WorldInfoValues'), True),
}
S['WorldInfoValues'] = {
    1: ('players', 'int', False),
    2: ('entities', 'int', False),
    3: ('tile_entities', 'int', False),
    4: ('chunks', 'int', False),
}

ENUMS = {
    'SamplerMode': {0: 'EXECUTION', 1: 'ALLOCATION', 2: 'LOCK'},
    'SamplerEngine': {0: 'JAVA', 1: 'ASYNC'},
    'ThreadDumperType': {0: 'ALL', 1: 'SPECIFIC', 2: 'REGEX'},
    'AggregatorType': {0: 'SIMPLE', 1: 'TICKED'},
    'ThreadGrouper': {0: 'BY_NAME', 1: 'BY_POOL', 2: 'AS_ONE'},
    'PlatformType': {0: 'SERVER', 1: 'CLIENT', 2: 'PROXY', 3: 'APPLICATION'},
    'SenderType': {0: 'OTHER', 1: 'PLAYER'},
    'OnlineMode': {0: 'UNKNOWN', 1: 'OFFLINE', 2: 'ONLINE'},
}


# --------------------------------------------------------------------------------------
# wire 解码器
# --------------------------------------------------------------------------------------

class WireError(Exception):
    pass


def _read_varint(buf, pos):
    result = 0
    shift = 0
    while True:
        if pos >= len(buf):
            raise WireError('truncated varint')
        b = buf[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, pos
        shift += 7
        if shift > 70:
            raise WireError('varint too long')


def _signed64(v):
    return v - (1 << 64) if v >= (1 << 63) else v


def _signed32(v):
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v >= (1 << 31) else v


def _decode_scalar(kind, buf, pos, wt):
    """读取单个标量（含 packable 重复字段的元素）。返回 (value, newpos)。"""
    if kind == 'double':
        if wt != 1:
            raise WireError('expect fixed64 for double')
        return struct.unpack('<d', buf[pos:pos + 8])[0], pos + 8
    if kind == 'float':
        if wt != 5:
            raise WireError('expect fixed32 for float')
        return struct.unpack('<f', buf[pos:pos + 4])[0], pos + 4
    if kind == 'int':
        if wt == 0:
            v, pos = _read_varint(buf, pos)
            return _signed64(v), pos
        if wt == 1:
            return struct.unpack('<q', buf[pos:pos + 8])[0], pos + 8
        raise WireError('bad wiretype for int')
    if kind == 'bool':
        v, pos = _read_varint(buf, pos)
        return bool(v), pos
    if kind == 'str':
        n, pos = _read_varint(buf, pos)
        raw = buf[pos:pos + n]
        pos += n
        return raw.decode('utf-8', 'replace'), pos
    if kind == 'bytes':
        n, pos = _read_varint(buf, pos)
        raw = buf[pos:pos + n]
        return raw, pos + n
    raise WireError('unknown scalar kind %r' % (kind,))


def _skip(buf, pos, wt):
    if wt == 0:
        _, pos = _read_varint(buf, pos)
        return pos
    if wt == 1:
        return pos + 8
    if wt == 2:
        n, pos = _read_varint(buf, pos)
        return pos + n
    if wt == 5:
        return pos + 4
    raise WireError('bad wiretype %d' % wt)


def _kind_of(spec):
    return spec[0]


def decode_message(buf, msg_name, depth=0, max_depth=64):
    """把 buf 解码成 dict（遵循 S 中的 schema）。未知字段以 'f<num>' 形式保留为 raw 值。"""
    if depth > max_depth:
        return {'__truncated__': True}
    fields = S.get(msg_name, {})
    out = {}
    pos = 0
    n = len(buf)
    while pos < n:
        key, pos = _read_varint(buf, pos)
        fnum, wt = key >> 3, key & 0x7
        spec = fields.get(fnum)
        if spec is None:
            # 未知字段：跳过，仅保留长度以便诊断
            start = pos
            pos = _skip(buf, pos, wt)
            out.setdefault('__unknown__', {})['f%d' % fnum] = pos - start
            continue
        name, kind, repeated = spec
        if isinstance(kind, tuple):
            tag = kind[0]
            if tag == 'msg':
                sub_name = kind[1]
                if wt != 2:
                    pos = _skip(buf, pos, wt)
                    continue
                ln, pos = _read_varint(buf, pos)
                val = decode_message(buf[pos:pos + ln], sub_name, depth + 1, max_depth)
                pos += ln
            elif tag == 'map':
                if wt != 2:
                    pos = _skip(buf, pos, wt)
                    continue
                ln, pos = _read_varint(buf, pos)
                k, v = _decode_map_entry(buf[pos:pos + ln], kind[1], kind[2], depth + 1, max_depth)
                pos += ln
                if name not in out:
                    out[name] = {}
                out[name][k] = v
                continue
            elif tag == 'enum':
                if wt == 0:
                    v, pos = _read_varint(buf, pos)
                    val = ENUMS.get(kind[1], {}).get(v, v)
                elif wt == 2:  # packed enum
                    ln, pos = _read_varint(buf, pos)
                    end = pos + ln
                    vals = []
                    while pos < end:
                        v, pos = _read_varint(buf, pos)
                        vals.append(ENUMS.get(kind[1], {}).get(v, v))
                    out[name] = vals
                    continue
                else:
                    pos = _skip(buf, pos, wt)
                    continue
            else:
                raise WireError('bad kind %r' % (kind,))
        else:
            kind_s = kind
            if wt == 2 and kind_s != 'str' and kind_s != 'bytes':
                # packed repeated scalar
                ln, pos = _read_varint(buf, pos)
                end = pos + ln
                vals = []
                elem_wt = {'int': 0, 'bool': 0, 'double': 1, 'float': 5}.get(kind_s, 0)
                while pos < end:
                    v, pos = _decode_scalar(kind_s, buf, pos, elem_wt)
                    vals.append(v)
                out.setdefault(name, []).extend(vals)
                continue
            val, pos = _decode_scalar(kind_s, buf, pos, wt)

        if repeated:
            out.setdefault(name, []).append(val)
        else:
            out[name] = val
    return out


def _decode_map_entry(buf, kkind, vkind, depth, max_depth):
    k = None
    v = None
    pos = 0
    n = len(buf)
    while pos < n:
        key, pos = _read_varint(buf, pos)
        fnum, wt = key >> 3, key & 0x7
        if fnum == 1:
            k, pos = _decode_scalar(kkind, buf, pos, wt)
        elif fnum == 2:
            if isinstance(vkind, tuple) and vkind[0] == 'msg':
                ln, pos = _read_varint(buf, pos)
                v = decode_message(buf[pos:pos + ln], vkind[1], depth + 1, max_depth)
                pos += ln
            else:
                v, pos = _decode_scalar(vkind, buf, pos, wt)
        else:
            pos = _skip(buf, pos, wt)
    return k, v


# --------------------------------------------------------------------------------------
# 加载
# --------------------------------------------------------------------------------------

def load_profile(path):
    with open(path, 'rb') as fh:
        raw = fh.read()
    if raw[:2] == b'\x1f\x8b':
        raw = gzip.decompress(raw)
    elif raw[:2] == b'PK':
        raise SystemExit('这是一个 zip 容器，不是 .sparkprofile')
    data = decode_message(raw, 'SamplerData')
    data['__meta_info__'] = {
        'file': os.path.abspath(path),
        'file_bytes': os.path.getsize(path),
        'compressed': False,
    }
    return data


# --------------------------------------------------------------------------------------
# 树构建
# --------------------------------------------------------------------------------------

class Node:
    __slots__ = ('class_name', 'method_name', 'line', 'desc', 'times', 'children', 'parent', '_refs')

    def __init__(self, class_name='', method_name='', line=0, desc=''):
        self.class_name = class_name
        self.method_name = method_name
        self.line = line
        self.desc = desc
        self.times = []
        self.children = []
        self.parent = None

    @property
    def label(self):
        """聚合/显示用标识：全限定类名#方法名，避免同名类（如多个 Main#main）互相污染。"""
        if not self.method_name:
            return self.class_name or '(root)'
        if not self.class_name:
            return self.method_name
        return '%s#%s' % (self.class_name, self.method_name)


def build_thread(thread):
    pool = thread.get('children') or []
    nodes = []
    for sn in pool:
        nd = Node(sn.get('class_name', ''), sn.get('method_name', ''),
                  sn.get('line_number', 0), sn.get('method_desc', ''))
        nd.times = sn.get('times') or []
        nd._refs = sn.get('children_refs') or []
        nodes.append(nd)
    for nd in nodes:
        for r in nd._refs:
            if 0 <= r < len(nodes):
                child = nodes[r]
                if child.parent is None:
                    child.parent = nd
                    nd.children.append(child)
    root = Node(thread.get('name', '?'))
    root.times = thread.get('times') or []
    for r in (thread.get('children_refs') or []):
        if 0 <= r < len(nodes):
            child = nodes[r]
            if child.parent is None:
                child.parent = root
                root.children.append(child)
    return root


def window_total(node):
    return sum(node.times) if node.times else 0.0


def windows_count(profile):
    md = profile.get('metadata') or {}
    best = 1
    for t in profile.get('threads') or []:
        if t.get('times'):
            best = max(best, len(t['times']))
            break
    if md.get('number_of_ticks') and md.get('time_windows'):
        best = max(best, len(md['time_windows']))
    return best


def interval_ms(profile):
    md = profile.get('metadata') or {}
    return md.get('interval') or 4000


# 当前报告的采样模式：EXECUTION = times 为毫秒；ALLOCATION = times 为字节；LOCK = 采样计数
CURRENT_MODE = 'EXECUTION'


def fmt_ms(value, _interval=None):
    """按当前采样模式格式化节点累计值。value 已是原生单位（EXECUTION 下为毫秒）。"""
    if CURRENT_MODE == 'ALLOCATION':
        if value >= 1 << 30:
            return '%.2f GiB' % (value / float(1 << 30))
        if value >= 1 << 20:
            return '%.2f MiB' % (value / float(1 << 20))
        if value >= 1024:
            return '%.1f KiB' % (value / 1024.0)
        return '%d B' % int(value)
    if CURRENT_MODE == 'LOCK':
        return '%d 次' % int(round(value))
    return fmt_dur(value)


def fmt_int(n):
    return format(int(n), ',')


def fmt_dur(ms):
    if ms >= 60000:
        return '%.2f min' % (ms / 60000.0)
    if ms >= 1000:
        return '%.2f s' % (ms / 1000.0)
    return '%.2f ms' % ms


def md_table(headers, rows):
    lines = ['| ' + ' | '.join(headers) + ' |',
             '|' + '|'.join(['---'] * len(headers)) + '|']
    for r in rows:
        lines.append('| ' + ' | '.join(str(c) for c in r) + ' |')
    return '\n'.join(lines)


def summarize(profile):
    md = profile.get('metadata') or {}
    pm = md.get('platform_metadata') or {}
    ps = md.get('platform_statistics') or {}
    da = md.get('data_aggregator') or {}
    td = md.get('thread_dumper') or {}
    creator = md.get('creator') or {}
    start = md.get('start_time') or 0
    end = md.get('end_time') or 0
    duration_ms = max(0, end - start)
    info = {
        'platform': pm.get('name'),
        'platform_type': pm.get('type') or 'SERVER',
        'brand': pm.get('brand'),
        'minecraft_version': pm.get('minecraft_version'),
        'spark_version': pm.get('spark_version'),
        'creator': creator.get('name'),
        'creator_type': creator.get('type'),
        'comment': md.get('comment'),
        'interval_ms': md.get('interval'),
        'mode': md.get('sampler_mode') or 'EXECUTION',
        'engine': md.get('sampler_engine') or 'JAVA',
        'engine_version': md.get('sampler_engine_version'),
        'aggregator': da.get('type') or 'SIMPLE',
        'thread_grouper': da.get('thread_grouper'),
        'tick_len_threshold': da.get('tick_length_threshold'),
        'included_ticks': da.get('number_of_included_ticks'),
        'thread_dumper': td.get('type'),
        'thread_patterns': td.get('patterns'),
        'start_time': start,
        'end_time': end,
        'duration_ms': duration_ms,
        'ticks': md.get('number_of_ticks'),
        'threads': len(profile.get('threads') or []),
        'windows': len(md.get('time_windows') or []),
        'tps': (ps.get('tps') or {}).get('last1m'),
        'mspt_1m': ((ps.get('mspt') or {}).get('last1m') or {}).get('mean'),
        'mspt_max_1m': ((ps.get('mspt') or {}).get('last1m') or {}).get('max'),
        'players': ps.get('player_count'),
        'entities': (ps.get('world') or {}).get('total_entities'),
    }
    return info


def cmd_info(profile, out):
    info = summarize(profile)
    md = profile.get('metadata') or {}
    rows = [
        ('文件', profile['__meta_info__']['file']),
        ('文件大小', fmt_int(profile['__meta_info__']['file_bytes']) + ' B'),
        ('平台', '%s %s' % (info['platform_type'], info['platform'])),
        ('品牌', info['brand']),
        ('Minecraft', info['minecraft_version']),
        ('spark', info['spark_version']),
        ('采样模式', info['mode']),
        ('采样引擎', '%s %s' % (info['engine'], info['engine_version'] or '')),
        ('聚合方式', '%s / 线程分组 %s' % (info['aggregator'], info['thread_grouper'])),
        ('线程筛选', '%s %s' % (info['thread_dumper'], info['thread_patterns'] or '')),
        ('采样间隔', '%s ms（%s µs）' % (_r((info['interval_ms'] or 0) / 1000.0, 2), info['interval_ms'])),
        ('时间', '%s → %s (%s)' % (ts(info['start_time']), ts(info['end_time']), fmt_dur(info['duration_ms']))),
        ('tick 数', info['ticks']),
        ('时间窗', info['windows']),
        ('线程数', info['threads']),
        ('TPS(1m)', info['tps']),
        ('MSPT 均值(1m)', info['mspt_1m']),
        ('MSPT 峰值(1m)', info['mspt_max_1m']),
        ('玩家/实体', '%s / %s' % (info['players'], info['entities'])),
        ('采样者', info['creator']),
        ('备注', info['comment']),
        ('server_configurations', len(md.get('server_configurations') or {})),
        ('sources(mod/插件)', len(md.get('sources') or {})),
    ]
    write(out, md_table(['项', '值'], rows))


def ts(ms):
    import datetime
    if not ms:
        return '-'
    try:
        return datetime.datetime.fromtimestamp(ms / 1000.0).strftime('%Y-%m-%d %H:%M:%S')
    except Exception:
        return str(ms)


def cmd_report(profile, out, top_n=25, depth=8, focus=None, no_idle=False, width=72):
    global CURRENT_MODE
    info = summarize(profile)
    CURRENT_MODE = info['mode']
    interval = interval_ms(profile)
    nwin = windows_count(profile)
    md = profile.get('metadata') or {}
    unit = {'EXECUTION': '毫秒', 'ALLOCATION': '分配字节', 'LOCK': '锁等待次数'}.get(CURRENT_MODE, '毫秒')
    L = []
    A = L.append

    A('# spark 性能报告')
    A('')
    A('> 由 `sparkprofile.py` 从 `.sparkprofile` 直接解析生成，不经过 spark 网页。')
    A('> 节点数值单位为**%s**（%s 模式）。' % (unit, CURRENT_MODE))
    A('')
    A('## 1. 概要')
    A('')
    A(md_table(['项', '值'], [
        ('文件', os.path.basename(info and profile['__meta_info__']['file'])),
        ('平台', '%s %s (%s)' % (info['platform_type'], info['platform'], info['brand'] or '')),
        ('版本', 'MC %s / spark %s' % (info['minecraft_version'], info['spark_version'])),
        ('采样模式', info['mode']),
        ('引擎', '%s %s' % (info['engine'], info['engine_version'] or '')),
        ('聚合', '%s，线程分组 %s' % (info['aggregator'], info['thread_grouper'])),
        ('采样间隔', '%s ms（%s µs）' % (_r(interval / 1000.0, 2), interval)),
        ('时间窗', '%d 个' % nwin),
        ('覆盖 tick', info['ticks']),
        ('时长', fmt_dur(info['duration_ms'])),
        ('TPS(1m)', _r(info['tps'])),
        ('MSPT 均值/峰值(1m)', '%s / %s' % (_r(info['mspt_1m']), _r(info['mspt_max_1m']))),
        ('玩家 / 实体', '%s / %s' % (_nz(info['players']), _nz(info['entities']))),
        ('采样者 / 备注', '%s / %s' % (_nz(info['creator']), _nz(info['comment']))),
    ]))
    A('')

    # 时间窗表
    tws = profile.get('time_window_statistics') or {}
    if tws and nwin > 1:
        A('## 2. 时间窗')
        A('')
        rows = []
        keys = sorted(tws.keys())
        for k in keys[:40]:
            w = tws[k]
            rows.append((_win_label(k), w.get('ticks'), _r(w.get('tps')), _r(w.get('mspt_median')),
                         _r(w.get('mspt_max')), w.get('players'), w.get('entities'),
                         w.get('chunks'), _r(w.get('cpu_process'))))
        if len(keys) > 40:
            rows.append(('...', '', '', '', '', '', '', '', '%d 窗口省略' % (len(keys) - 40)))
        A(md_table(['窗', 'ticks', 'TPS', 'MSPT中位', 'MSPT峰', '玩家', '实体', '区块', 'CPU(进程)'], rows))
        A('')

    # 线程排名
    A('## 3. 线程开销排名')
    A('')
    threads = iter_roots(profile, focus)
    total = sum(v for _, v in threads) or 1.0
    rows = []
    for root, tot in threads[:top_n]:
        rows.append((root.label, fmt_ms(tot, interval), '%.1f%%' % (100.0 * tot / total)))
    A(md_table(['线程', '累计占用时间', '占比'], rows))
    A('')

    # 热点方法（self time / total time）与命名空间聚合
    self_acc, total_acc, grand = aggregate(threads, no_idle, top_n=None)
    grand = grand or 1.0

    A('## 4. 热点方法（自身耗时 self time）')
    A('')
    A('> self time = 栈帧自身的累计时间（已扣除子调用），用于定位真正消耗时间的方法。')
    if no_idle:
        A('> 已排除 park / await / wait / poll 等阻塞空闲栈帧，占比为“有效工作时间”口径。')
    A('')
    rows = [(label, fmt_ms(v, interval), '%.2f%%' % (100.0 * v / grand))
            for label, v in sorted(self_acc.items(), key=lambda x: -x[1])[:top_n]]
    A(md_table(['方法', 'self 时间', '占比'], rows))
    A('')

    A('## 5. 热点方法（总耗时 total time，含子调用）')
    A('')
    A('> 同一方法可能出现在多条调用路径上，占比之和可超过 100%，属正常现象。')
    A('')
    rows = [(label, fmt_ms(v, interval), '%.2f%%' % (100.0 * v / grand))
            for label, v in sorted(total_acc.items(), key=lambda x: -x[1])[:top_n]]
    A(md_table(['方法', 'total 时间', '占比'], rows))
    A('')

    A('## 6. 按命名空间聚合（self time）')
    A('')
    ns_acc = defaultdict(float)
    for root, _ in threads:
        stack = [root]
        while stack:
            nd = stack.pop()
            v = self_of(nd, no_idle)
            if v and nd.class_name:
                ns_acc[_namespace(nd.class_name)] += v
            stack.extend(nd.children)
    rows = [(ns, fmt_ms(v, interval), '%.2f%%' % (100.0 * v / grand))
            for ns, v in sorted(ns_acc.items(), key=lambda x: -x[1])[:20]]
    A(md_table(['命名空间', 'self 时间', '占比'], rows))
    A('')

    # 主线程调用树
    target = focus
    if target is None:
        # 选择累计最高的线程
        target = threads[0][0].label if threads else None
    if target:
        root = None
        for r, _ in threads:
            if r.label == target or target in r.label:
                root = r
                break
        if root is not None:
            A('## 7. 调用树：%s（top 子树）' % root.label)
            A('')
            A('```')
            A(render_tree(root, interval, max_depth=depth, min_pct=0.5, width=width))
            A('```')
            A('')

    A('## 8. 原始统计')
    A('')
    A('完整结构可用 `sparkprofile.py json` 导出为 JSON。')
    write(out, '\n'.join(L))


def _nz(v):
    return '-' if v is None or v == '' else v


def _win_label(key):
    """时间窗 key 为 epoch 分钟数（System.currentTimeMillis() / 60000）。"""
    import datetime
    try:
        dt = datetime.datetime.fromtimestamp(int(key) * 60)
        return dt.strftime('%H:%M')
    except Exception:
        return str(key)


def _r(v, n=2):
    if v is None:
        return '-'
    try:
        return round(float(v), n)
    except Exception:
        return v


_DEEP_PREFIX = {'github', 'google', 'apache', 'eclipse', 'bukkit', 'spigot', 'modlauncher'}


def _namespace(class_name):
    """把类名归约到命名空间级别，用于按 mod / 库聚合。"""
    parts = class_name.split('.')
    if len(parts) <= 2:
        return class_name
    n = 3
    if len(parts) >= 4 and parts[1] in _DEEP_PREFIX:
        n = 4
    return '.'.join(parts[:n])


def _fold_chain(nd, tol=0.01, max_frames=6):
    """把「只有一个孩子且耗时基本相同」的链折叠成一行，压缩启动/框架样板栈。"""
    chain = [nd.label]
    cur = nd
    while True:
        if len(cur.children) != 1:
            break
        child = cur.children[0]
        cur_t, ch_t = window_total(cur), window_total(child)
        if cur_t <= 0 or abs(cur_t - ch_t) > cur_t * tol:
            break
        chain.append(child.label)
        cur = child
        if len(chain) >= max_frames:
            break
    if len(chain) > 3:
        shown = [chain[0], '…', chain[-1]]
    else:
        shown = chain
    return ' → '.join(shown), cur


def render_tree(root, interval, max_depth=12, min_pct=0.0, prefix='', max_children=40, width=72):
    lines = []
    base = window_total(root) or 1.0

    def walk(node, depth, prefix, is_last):
        if depth > max_depth:
            return
        label, nd = (node.label, node) if depth == 0 else _fold_chain(node)
        tot = window_total(nd)
        pct = 100.0 * tot / base if base else 0.0
        if pct < min_pct and depth > 0:
            return
        conn = '' if depth == 0 else ('└─ ' if is_last else '├─ ')
        avail = max(width - len(prefix) - len(conn), 36)
        lab = label if len(label) <= avail else label[:avail - 1] + '…'
        lines.append(prefix + conn + lab.ljust(avail))
        lines[-1] += ' %9s %6.2f%%' % (fmt_ms(tot, interval), pct)
        kids = sorted(nd.children, key=lambda c: -window_total(c))
        if len(kids) > max_children:
            kids = kids[:max_children]
        for i, c in enumerate(kids):
            walk(c, depth + 1, prefix + ('' if depth == 0 else ('   ' if is_last else '│  ')),
                 i == len(kids) - 1)

    walk(root, 0, '', True)
    return '\n'.join(lines)


def cmd_tree(profile, out, thread=None, depth=12, min_pct=0.0, top_n=10, width=72):
    global CURRENT_MODE
    CURRENT_MODE = summarize(profile)['mode']
    interval = interval_ms(profile)
    threads = []
    for t in profile.get('threads') or []:
        root = build_thread(t)
        threads.append((root, window_total(root)))
    threads.sort(key=lambda x: -x[1])
    if not threads:
        write(out, '（无采样数据）')
        return
    if thread:
        sel = [x for x in threads if thread.lower() in x[0].label.lower()]
        if not sel:
            write(out, '未匹配线程 %r。可用线程：\n%s' % (
                thread, '\n'.join(r.label for r, _ in threads[:30])))
            return
        targets = sel[:1]
    else:
        targets = threads[:top_n]
    chunks = []
    for root, _ in targets:
        chunks.append('=== %s  (总计 %s) ===' % (root.label, fmt_ms(window_total(root), interval)))
        if depth > 0:
            chunks.append(render_tree(root, interval, max_depth=depth, min_pct=min_pct, width=width))
            chunks.append('')
    write(out, '\n'.join(chunks))


IDLE_FRAMES = {
    'park', 'parkNanos', 'parkUntil', 'await', 'awaitNanos', 'awaitTermination', 'wait', 'wait0',
    'sleep', 'sleep0', 'take', 'poll', 'pollFirst', 'pollLast', 'getTask', 'unmanagedBlock',
    'managedBlock', 'spinThenWait', 'spinWait', 'ConditionNode#block', 'acquire',
    'GetQueuedCompletionStatus0', 'epollWait', 'keventWait', 'waitForWork',
    'waitForReferencePendingList', 'select', 'selectNow', 'yield',
}


def is_idle(label):
    """判断是否属于常见的阻塞/空闲栈帧（线程池 park、等待、poll 等）。"""
    if not label:
        return False
    method = label.rsplit('#', 1)[-1]
    return method in IDLE_FRAMES


def self_of(nd, no_idle=False):
    """单节点 self 时间；no_idle 时阻塞空闲帧记 0。"""
    if no_idle and is_idle(nd.label):
        return 0.0
    return max(0.0, window_total(nd) - sum(window_total(c) for c in nd.children))


def adjusted_totals(roots, no_idle=False):
    """返回 {id(node): 调整后 total}；no_idle 时剔除空闲子树的耗时。"""
    memo = {}

    def calc(nd):
        key = id(nd)
        if key in memo:
            return memo[key]
        v = self_of(nd, no_idle) + sum(calc(c) for c in nd.children)
        memo[key] = v
        return v

    for r, _ in roots:
        calc(r)
    return memo


def aggregate(roots, no_idle=False, top_n=None):
    """汇总 self / total / 总计，供 top、pkg、report 复用。"""
    adj = adjusted_totals(roots, no_idle)
    self_acc = defaultdict(float)
    total_acc = defaultdict(float)
    occurrences = defaultdict(int)
    for root, _ in roots:
        stack = [root]
        while stack:
            nd = stack.pop()
            sv = self_of(nd, no_idle)
            if sv:
                self_acc[nd.label] += sv
            tv = adj.get(id(nd), 0.0)
            if tv:
                total_acc[nd.label] += tv
            occurrences[nd.label] += 1
            stack.extend(nd.children)
    grand = sum(adj.get(id(r), 0.0) for r, _ in roots)
    return self_acc, total_acc, grand


def iter_roots(profile, thread=None, sort=True):
    """返回 [(root, total)]，可按线程名子串过滤。"""
    items = []
    for t in profile.get('threads') or []:
        root = build_thread(t)
        items.append((root, window_total(root)))
    if thread:
        low = thread.lower()
        items = [x for x in items if low in x[0].label.lower()]
    if sort:
        items.sort(key=lambda x: -x[1])
    return items


def cmd_top(profile, out, mode='self', limit=30, thread=None, no_idle=False):
    global CURRENT_MODE
    CURRENT_MODE = summarize(profile)['mode']
    interval = interval_ms(profile)
    roots = iter_roots(profile, thread)
    self_acc, total_acc, grand = aggregate(roots, no_idle)
    grand = grand or 1.0
    acc = self_acc if mode == 'self' else total_acc
    rows = []
    for label, v in sorted(acc.items(), key=lambda x: -x[1])[:limit]:
        rows.append((label, fmt_ms(v, interval), '%.2f%%' % (100.0 * v / grand)))
    write(out, md_table(['方法', '%s 时间' % mode, '占比'], rows))


def cmd_pkg(profile, out, limit=30, thread=None, no_idle=False):
    global CURRENT_MODE
    CURRENT_MODE = summarize(profile)['mode']
    interval = interval_ms(profile)
    roots = iter_roots(profile, thread)
    _, _, grand = aggregate(roots, no_idle)
    grand = grand or 1.0
    acc = defaultdict(float)
    for root, _ in roots:
        stack = [root]
        while stack:
            nd = stack.pop()
            v = self_of(nd, no_idle)
            if v and nd.class_name:
                acc[_namespace(nd.class_name)] += v
            stack.extend(nd.children)
    rows = [(ns, fmt_ms(v, interval), '%.2f%%' % (100.0 * v / grand))
            for ns, v in sorted(acc.items(), key=lambda x: -x[1])[:limit]]
    write(out, md_table(['命名空间', 'self 时间', '占比'], rows))


def cmd_json(profile, out, slim=False, pretty=True):
    data = profile
    if slim:
        data = {
            'meta_info': profile.get('__meta_info__'),
            'summary': summarize(profile),
            'threads': [
                {'name': t.get('name'), 'times': t.get('times'),
                 'total_samples': sum(t.get('times') or []),
                 'nodes': len(t.get('children') or [])}
                for t in (profile.get('threads') or [])
            ],
            'time_window_statistics': profile.get('time_window_statistics'),
            'sources': list((profile.get('metadata') or {}).get('sources', {}).keys()),
        }
    else:
        data = _strip_internal(profile)
    write(out, json.dumps(data, ensure_ascii=False, indent=2 if pretty else None, default=_json_default))


def _json_default(o):
    if isinstance(o, bytes):
        return o.hex()
    if isinstance(o, set):
        return sorted(o)
    return str(o)


def _strip_internal(profile):
    out = dict(profile)
    out.pop('__meta_info__', None)
    # 去掉体积巨大的源码映射（一般用不到）
    for k in ('class_sources', 'method_sources', 'line_sources'):
        if k in out:
            out[k] = {'__omitted__': len(out[k])}
    return out


def cmd_diff(profile_a, profile_b, out, limit=20):
    global CURRENT_MODE
    ia, ib = summarize(profile_a), summarize(profile_b)
    CURRENT_MODE = ia['mode']
    A = []
    A.append('# spark 报告对比')
    A.append('')
    A.append(md_table(['指标', os.path.basename(ia and profile_a['__meta_info__']['file']),
                os.path.basename(profile_b['__meta_info__']['file'])], [
        ('时长', fmt_dur(ia['duration_ms']), fmt_dur(ib['duration_ms'])),
        ('tick 数', ia['ticks'], ib['ticks']),
        ('TPS', _r(ia['tps']), _r(ib['tps'])),
        ('MSPT 均值', _r(ia['mspt_1m']), _r(ib['mspt_1m'])),
        ('MSPT 峰值', _r(ia['mspt_max_1m']), _r(ib['mspt_max_1m'])),
        ('实体', ia['entities'], ib['entities']),
    ]))
    A.append('')
    A.append('## self time 差异（B - A）')
    A.append('')
    a = _self_map(profile_a)
    b = _self_map(profile_b)
    keys = set(a) | set(b)
    diffs = sorted(keys, key=lambda k: -(b.get(k, 0) - a.get(k, 0)))
    rows = []
    for k in diffs[:limit]:
        rows.append((k, round(a.get(k, 0.0), 1), round(b.get(k, 0.0), 1), round(b.get(k, 0.0) - a.get(k, 0.0), 1)))
    A.append(md_table(['方法', 'A self', 'B self', '变化'], rows))
    write(out, '\n'.join(A))


def _self_map(profile):
    acc = defaultdict(float)
    for t in profile.get('threads') or []:
        root = build_thread(t)
        stack = [root]
        while stack:
            nd = stack.pop()
            acc[nd.label] += max(0.0, window_total(nd) - sum(window_total(c) for c in nd.children))
            stack.extend(nd.children)
    return acc


def write(out, text):
    if out:
        with io.open(out, 'w', encoding='utf-8', newline='\n') as fh:
            fh.write(text + '\n')
        sys.stderr.write('written: %s\n' % out)
    else:
        sys.stdout.write(text + '\n')


# --------------------------------------------------------------------------------------

def main(argv=None):
    ap = argparse.ArgumentParser(description='解析 spark .sparkprofile 为可读文本/JSON')
    sub = ap.add_subparsers(dest='cmd')

    def common(p):
        p.add_argument('file')
        p.add_argument('-o', '--out')

    p = sub.add_parser('info', help='概要信息'); common(p)
    p = sub.add_parser('report', help='完整 markdown 报告')
    common(p)
    p.add_argument('--top-n', type=int, default=25)
    p.add_argument('--depth', type=int, default=8)
    p.add_argument('--thread', default=None, help='指定调用树展示的线程')
    p.add_argument('--no-idle', action='store_true', help='排除 park/await/wait/poll 等阻塞空闲栈帧')
    p.add_argument('--width', type=int, default=72, help='调用树标签列宽')
    p = sub.add_parser('tree', help='调用树')
    common(p)
    p.add_argument('--thread', default=None)
    p.add_argument('--depth', type=int, default=12)
    p.add_argument('--min-pct', type=float, default=0.0)
    p.add_argument('--width', type=int, default=72, help='调用树标签列宽')
    p.add_argument('--top', type=int, default=5, help='无 --thread 时输出前 N 个线程')
    p = sub.add_parser('top', help='热点方法')
    common(p)
    p.add_argument('--self', dest='mode', action='store_const', const='self')
    p.add_argument('--total', dest='mode', action='store_const', const='total')
    p.set_defaults(mode='self')
    p.add_argument('--limit', type=int, default=30)
    p.add_argument('--thread', default=None, help='仅统计名称包含该子串的线程')
    p.add_argument('--no-idle', action='store_true', help='排除 park/await/wait/poll 等阻塞空闲栈帧')
    p = sub.add_parser('pkg', help='命名空间聚合'); common(p)
    p.add_argument('--limit', type=int, default=30)
    p.add_argument('--thread', default=None, help='仅统计名称包含该子串的线程')
    p.add_argument('--no-idle', action='store_true', help='排除 park/await/wait/poll 等阻塞空闲栈帧')
    p = sub.add_parser('json', help='导出 JSON'); common(p)
    p.add_argument('--slim', action='store_true')
    p.add_argument('--compact', action='store_true')
    p = sub.add_parser('diff', help='两份报告对比')
    p.add_argument('a'); p.add_argument('b'); p.add_argument('-o', '--out')
    p.add_argument('--limit', type=int, default=20)

    args = ap.parse_args(argv)
    if not args.cmd:
        ap.print_help()
        return 1

    if args.cmd == 'diff':
        cmd_diff(load_profile(args.a), load_profile(args.b), args.out, args.limit)
        return 0

    profile = load_profile(args.file)
    if args.cmd == 'info':
        cmd_info(profile, args.out)
    elif args.cmd == 'report':
        cmd_report(profile, args.out, args.top_n, args.depth, args.thread, args.no_idle, args.width)
    elif args.cmd == 'tree':
        cmd_tree(profile, args.out, args.thread, args.depth, args.min_pct, args.top, args.width)
    elif args.cmd == 'top':
        cmd_top(profile, args.out, args.mode, args.limit, args.thread, args.no_idle)
    elif args.cmd == 'pkg':
        cmd_pkg(profile, args.out, args.limit, args.thread, args.no_idle)
    elif args.cmd == 'json':
        cmd_json(profile, args.out, args.slim, not args.compact)
    return 0


if __name__ == '__main__':
    sys.exit(main())
