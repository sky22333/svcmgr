package com.androidservice.singbox

import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.TunOptions

class SingBoxVpnPlatform(
    private val vpnService: VpnService,
    private val onTunInterfaceChanged: (ParcelFileDescriptor) -> Unit,
) : SingBoxPlatform() {

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        vpnService.protect(fd)
    }

    override fun openTun(options: TunOptions): Int {
        if (VpnService.prepare(vpnService) != null) error("missing vpn permission")

        val builder = vpnService.Builder()
            .setSession("sing-box")
            .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val inet4Address = options.inet4Address
        while (inet4Address.hasNext()) {
            val address = inet4Address.next()
            builder.addAddress(address.address(), address.prefix())
        }

        val inet6Address = options.inet6Address
        while (inet6Address.hasNext()) {
            val address = inet6Address.next()
            builder.addAddress(address.address(), address.prefix())
        }

        if (options.autoRoute) {
            if (options.dnsMode.value != Libbox.DNSModeDisabled) {
                val dnsServerAddress = options.dnsServerAddress
                while (dnsServerAddress.hasNext()) {
                    builder.addDnsServer(dnsServerAddress.next())
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val inet4RouteAddress = options.inet4RouteAddress
                if (inet4RouteAddress.hasNext()) {
                    while (inet4RouteAddress.hasNext()) {
                        builder.addRoute(inet4RouteAddress.next().toIpPrefix())
                    }
                } else if (options.inet4Address.hasNext()) {
                    builder.addRoute("0.0.0.0", 0)
                }

                val inet6RouteAddress = options.inet6RouteAddress
                if (inet6RouteAddress.hasNext()) {
                    while (inet6RouteAddress.hasNext()) {
                        builder.addRoute(inet6RouteAddress.next().toIpPrefix())
                    }
                } else if (options.inet6Address.hasNext()) {
                    builder.addRoute("::", 0)
                }

                val inet4RouteExcludeAddress = options.inet4RouteExcludeAddress
                while (inet4RouteExcludeAddress.hasNext()) {
                    builder.excludeRoute(inet4RouteExcludeAddress.next().toIpPrefix())
                }

                val inet6RouteExcludeAddress = options.inet6RouteExcludeAddress
                while (inet6RouteExcludeAddress.hasNext()) {
                    builder.excludeRoute(inet6RouteExcludeAddress.next().toIpPrefix())
                }
            } else {
                val inet4RouteAddress = options.inet4RouteRange
                while (inet4RouteAddress.hasNext()) {
                    val address = inet4RouteAddress.next()
                    builder.addRoute(address.address(), address.prefix())
                }

                val inet6RouteAddress = options.inet6RouteRange
                while (inet6RouteAddress.hasNext()) {
                    val address = inet6RouteAddress.next()
                    builder.addRoute(address.address(), address.prefix())
                }
            }

            val includePackage = options.includePackage
            while (includePackage.hasNext()) {
                builder.addAllowedApplication(includePackage.next())
            }

            val excludePackage = options.excludePackage
            while (excludePackage.hasNext()) {
                builder.addDisallowedApplication(excludePackage.next())
            }
        }

        val pfd = builder.establish() ?: error("vpn establish failed")
        onTunInterfaceChanged(pfd)
        return pfd.fd
    }
}
