from scapy.all import *
import sqlite3
import sys
from pprint import pprint

GSM_PACKET_QUERY = '''SELECT * FROM GSMPacket'''

if len(sys.argv) != 3:
    print("Usage: python packetdumper.py <db-path> <pcap-dir>")
    sys.exit(-1)

sqlitedb = sys.argv[1]
pcapdir = sys.argv[2]
conn = sqlite3.connect(sqlitedb)

'''
struct gsmtap_hdr {
    uint8_t version;        /* version, set to 0x01 currently */
    uint8_t hdr_len;        /* length in number of 32bit words */
    uint8_t type;           /* see GSMTAP_TYPE_* */
    uint8_t timeslot;       /* timeslot (0..7 on Um) */

    uint16_t arfcn;         /* ARFCN (frequency) */
    int8_t signal_dbm;      /* signal level in dBm */
    int8_t snr_db;          /* signal/noise ratio in dB */

    uint32_t frame_number;  /* GSM Frame Number (FN) */

    uint8_t sub_type;       /* Type of burst/channel, see above */
    uint8_t antenna_nr;     /* Antenna Number */
    uint8_t sub_slot;       /* sub-slot within timeslot */
    uint8_t res;            /* reserved for future use (RFU) */
}
'''

def construct_gsmtap_packet(pac_data):
    packet_bytes = bytearray()
    # Version (uint8)
    packet_bytes += bytearray(b'\x02')
    # Header len (uint8)
    packet_bytes += bytearray(b'\x04')
    # Type (uint8)
    tmpbytes = pac_data['type'].to_bytes(1, 'big')
    packet_bytes += tmpbytes
    # timeslot (uint8)
    tmpbytes = pac_data['timeslot'].to_bytes(1, 'big')
    packet_bytes += tmpbytes
    # arfcn (uint16)
    tmpbytes = pac_data['arfcn'].to_bytes(2, 'big')
    packet_bytes += tmpbytes
    # signal_dbm (int8)
    tmpbytes = (pac_data['dbm'] & 0xff).to_bytes(1, 'big')
    packet_bytes += tmpbytes
    # snr (int8)
    packet_bytes += bytearray(b'\x00')
    # frame_number (uint32)
    tmpbytes = pac_data['frameNo'].to_bytes(4, 'big')
    packet_bytes += tmpbytes
    # sub_type (uint8)
    tmpbytes = pac_data['subtype'].to_bytes(1, 'big')
    packet_bytes += tmpbytes
    # antenna_nr (uint8)
    packet_bytes += bytearray(b'\x00')
    # sub_slot (uint8)
    packet_bytes += bytearray(b'\x00')
    # res (uint8)
    packet_bytes += bytearray(b'\x00')
    # Add the 23 byte payload
    packet_bytes += pac_data['payload']

    return bytes(packet_bytes)

with conn:
    cur = conn.cursor()
    packet_query = cur.execute(GSM_PACKET_QUERY)
    gsmtap_pacs = []

    for raw_pac in packet_query:
        pac_data = {}
        
        # This is hardcoded for now beware if the columns change
        pac_data['type'] = raw_pac[1] 
        pac_data['subtype'] = raw_pac[2] 
        pac_data['timeslot'] = raw_pac[3] 
        pac_data['frameNo'] = raw_pac[4] 
        pac_data['payload'] = raw_pac[5] 
        pac_data['timestamp'] = raw_pac[6]
        pac_data['band'] = raw_pac[7] 
        pac_data['arfcn'] = raw_pac[8] 
        pac_data['dbm'] = raw_pac[9] 
        gsmtap_payload = construct_gsmtap_packet(pac_data)

        gsmtap_pac = IP(dst="127.0.0.1")/UDP(sport=12345, dport=4729)/Raw(gsmtap_payload)
        gsmtap_pac.time = pac_data['timestamp'] / 1000
        gsmtap_pacs.append(gsmtap_pac)

wrpcap(pcapdir, gsmtap_pacs)
