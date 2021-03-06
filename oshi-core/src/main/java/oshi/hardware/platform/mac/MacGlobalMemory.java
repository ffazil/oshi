/**
 * OSHI (https://github.com/oshi/oshi)
 *
 * Copyright (c) 2010 - 2019 The OSHI Project Team:
 * https://github.com/oshi/oshi/graphs/contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package oshi.hardware.platform.mac;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Native; // NOSONAR
import com.sun.jna.platform.mac.SystemB;
import com.sun.jna.platform.mac.SystemB.VMStatistics;
import com.sun.jna.platform.mac.SystemB.XswUsage;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

import oshi.hardware.common.AbstractGlobalMemory;
import oshi.util.ParseUtil;
import oshi.util.platform.mac.SysctlUtil;

/**
 * Memory obtained by host_statistics (vm_stat) and sysctl
 *
 * @author widdis[at]gmail[dot]com
 */
public class MacGlobalMemory extends AbstractGlobalMemory {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(MacGlobalMemory.class);

    private transient XswUsage xswUsage = new XswUsage();
    private long lastUpdateSwap = 0;

    private transient VMStatistics vmStats = new VMStatistics();
    private long lastUpdateAvail = 0;

    public MacGlobalMemory() {
        long memory = SysctlUtil.sysctl("hw.memsize", -1L);
        if (memory >= 0) {
            this.memTotal = memory;
        }

        LongByReference pPageSize = new LongByReference();
        if (0 != SystemB.INSTANCE.host_page_size(SystemB.INSTANCE.mach_host_self(), pPageSize)) {
            LOG.error("Failed to get host page size. Error code: {}", Native.getLastError());
            return;
        }
        this.pageSize = pPageSize.getValue();
    }

    /**
     * Updates available memory no more often than every 100ms
     */
    @Override
    protected void updateMeminfo() {
        long now = System.currentTimeMillis();
        if (now - this.lastUpdateAvail > 100) {
            if (0 != SystemB.INSTANCE.host_statistics(SystemB.INSTANCE.mach_host_self(), SystemB.HOST_VM_INFO,
                    this.vmStats, new IntByReference(this.vmStats.size() / SystemB.INT_SIZE))) {
                LOG.error("Failed to get host VM info. Error code: {}", Native.getLastError());
                return;
            }
            this.memAvailable = (this.vmStats.free_count + this.vmStats.inactive_count) * this.pageSize;
            this.swapPagesIn = ParseUtil.unsignedIntToLong(this.vmStats.pageins);
            this.swapPagesOut = ParseUtil.unsignedIntToLong(this.vmStats.pageouts);
            this.lastUpdateAvail = now;
        }
    }

    /**
     * Updates swap file stats no more often than every 100ms
     */
    @Override
    protected void updateSwap() {
        long now = System.currentTimeMillis();
        if (now - this.lastUpdateSwap > 100) {
            if (!SysctlUtil.sysctl("vm.swapusage", this.xswUsage)) {
                return;
            }
            this.swapUsed = this.xswUsage.xsu_used;
            this.swapTotal = this.xswUsage.xsu_total;
            this.lastUpdateSwap = now;
        }
    }
}
