<p align="center"><img src="assets/image/app_icon.png" width="96" alt="Proxy Detector icon"></p>

# Private space and per-app split tunneling bypass POC

![](https://github.com/runetfreedom/per-app-split-bypass-poc/raw/master/assets/image/preview.png)

This repository is a POC for a vulnerability that should be called the "localhost attack"

Almost all mobile clients based on xray/sing-box run a local socks5 proxy without authentication.

At the same time, per-app split tunneling is implemented using VpnService, which redirects traffic to tun2socks (or something similar). But if there is spyware on the user's device (for example, as part of a government application), nothing prevents it from connecting directly to this socks5 proxy, bypassing VpnService, and discovering the user's external IP address.

The situation is complicated by the fact that Android private spaces (Knox, Shelter, Island, etc), while isolating VpnService, do not isolate the loopback interface. This means that even if the spyware is located inside a private space, it is still possible to scan all localhost ports and find the socks5 proxy. This gives the user a false sense of security.

I was prompted to research this vulnerability by the [discovery of spyware](https://habr.com/ru/articles/1006666/) in the Russian national messenger Max, which checked the success of WhatsApp and Telegram blocks, and also tried to find the external IP of the VPN service.

## Proposed solution

I propose always running the socks5 proxy with authentication. Furthermore, it should use a randomly generated login/password on each device to prevent brute-force attacks.

# Xray API

An even greater vulnerability is launching Xray with `HandlerService` enabled. In this case, an attacker can not only find out the client's external IP, but also dump the outbound configuration there. This will allow the attacker to find out the connection address, reality sni, uuid, and so on.

This vulnerability is hazardous, but fortunately, I only found one client that would do this.