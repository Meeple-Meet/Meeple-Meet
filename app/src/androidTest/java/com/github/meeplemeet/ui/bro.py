from scapy.all import *

def convert_sciper_to_ip(sciper):
    n = 2
    split_sciper = [sciper[i:i+n] for i in range(0, len(sciper), n)]
    return "00." + '.'.join(split_sciper)

def modify_pcap(input_pcap_file, output_pcap_file):

    new_pkts = []
    pkts = rdpcap(input_pcap_file)

    SCIPER = "377690"
    new_ip = convert_sciper_to_ip(SCIPER)

    for packet in pkts:

        if not (packet.haslayer(DNS) and packet[DNS].qr == 1):
            new_pkts.append(packet)
            continue

        original_time = packet.time
        dns = packet[DNS]
        modified = False

        def fix_section(section, count):
            nonlocal modified
            for i in range(count):
                rr = section[i]
                if rr.type == 1:           # ← MODIFY ALL A RECORDS
                    rr.rdata = new_ip
                    modified = True

        fix_section(dns.an, dns.ancount)
        fix_section(dns.ns, dns.nscount)
        fix_section(dns.ar, dns.arcount)

        if not modified:
            new_pkts.append(packet)
            continue

        if packet.haslayer(IP):
            del packet[IP].len
            del packet[IP].chksum
        if packet.haslayer(UDP):
            del packet[UDP].len
            del packet[UDP].chksum

        packet.time = original_time
        new_pkts.append(packet)

    wrpcap(output_pcap_file, new_pkts)


if __name__ == "__main__":
    modify_pcap("/Users/nilsamaha/Downloads/pcap2.pcap", "/Users/nilsamaha/Downloads/pcap2new.pcap")