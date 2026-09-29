#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""汇总 testDebugUnitTest 结果。"""
import glob
import xml.etree.ElementTree as ET

t = f = 0
files = glob.glob('app/build/test-results/testDebugUnitTest/*.xml')
for p in files:
    r = ET.parse(p).getroot()
    t += int(r.get('tests', 0))
    f += int(r.get('failures', 0)) + int(r.get('errors', 0))
print('suites=%d tests=%d failures=%d' % (len(files), t, f))
