package com.kaltok.tcpip.common.util

import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Enumeration

object NetworkUtils {
    fun getLocalIpAddress(): List<String> {
        val ipAddresses: MutableList<String> = ArrayList()
        val loopBackAddressList = mutableListOf<String>()
        try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface: NetworkInterface = interfaces.nextElement()
                if (networkInterface.isUp) {
                    val addresses: Enumeration<InetAddress> = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address: InetAddress = addresses.nextElement()
                        if (!address.isLoopbackAddress) {
                            ipAddresses.add(address.hostAddress)
                        }else{
                            loopBackAddressList.add(address.hostAddress)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ipAddresses
    }
}