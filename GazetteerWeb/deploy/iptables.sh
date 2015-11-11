#!/bin/sh

iptables -A INPUT -i lo -j ACCEPT

iptables -A INPUT -p tcp --dport 9200 -j DROP
iptables -A INPUT -p tcp --dport 9300 -j DROP
iptables -A INPUT -p tcp --dport 8080 -j DROP