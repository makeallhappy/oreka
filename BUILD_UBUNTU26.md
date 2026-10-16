# Building Oreka C++ on Ubuntu 26.04

This document describes how to build the C++ parts of Oreka (orkbasecxx + orkaudio)
on a modern Ubuntu 26.04 system, and the changes made to the `updateOldLibs` branch
to make it work with Boost 1.90 and current toolchains.

The original `BUILD_C++.txt` targets Ubuntu 18.04 and requires manually building
Opus 1.2.1 and the SILK SDK into `/opt`. This branch removes those requirements.

## TL;DR

```bash
# 1. Install all dependencies from apt (no manual /opt builds needed)
sudo apt-get install -y \
  libboost-dev libapr1-dev liblog4cxx-dev libpcap-dev libxerces-c-dev \
  libsndfile1-dev libspeex-dev libdw-dev libunwind-dev \
  libcap-dev libbackward-cpp-dev libbcg729-dev \
  autoconf automake libtool g++ make git

# 2. Build orkbasecxx
cd orkbasecxx
autoreconf -i && ./configure CXX=g++ && make -j$(nproc)

# 3. Build orkaudio (needs to find liborkbase)
cd ../orkaudio
autoreconf -i && ./configure CXX=g++
export LIBRARY_PATH=$PWD/../orkbasecxx/.libs
make -j$(nproc)
```

Binaries:
- `orkbasecxx/.libs/liborkbase.so` — core library
- `orkaudio/orkaudio` — main daemon
- Plugins: `libvoip.so` (SIP/RTP capture), `libgenerator.so` (test),
  `librtpmixer.so`, `libg729codec.so`

To run orkaudio, either install orkbase to the system or set the library path:

```bash
sudo make install        # in orkbasecxx, installs to /usr/lib
# or
export LD_LIBRARY_PATH=$PWD/../orkbasecxx/.libs
```

## Changes on this branch

### 1. Removed optional codec plugins with heavy external deps

| Plugin | Original dependency | Status |
|---|---|---|
| SILK codec | SILK SDK manually built to `/opt/silk` | Removed (Skype-era codec) |
| Opus codec + OggOpusFile | Opus 1.2.1 manually built to `/opt/opus` | Removed; recordings configured as `opus` now fall through to PCM WAV |
| LiveStream filter | cpp-httplib + srs_librtmp | Removed (disabled by default anyway) |
| G.729 codec | bcg729 manually built to `/opt/bcg729` | **Kept**, switched to system `libbcg729-dev` |

Removal method:
- Pure runtime plugins (SILK, G.729, LiveStream): drop from
  `orkaudio/filters/Makefile.am` `SUBDIRS` and `orkaudio/configure.in` `AC_OUTPUT`.
- Statically-linked Opus: additionally removed the `#include` + `new OpusCodecDecoder()`
  registrations in `orkaudio/OrkAudio.cpp`, the `new OggOpusFile()` factory branch in
  `orkbasecxx/BatchProcessing.cpp`, and all `-L/opt/opus` / `-lopusstatic` /
  `-I/opt/opus/include` references in the Makefile.am files.
- Defensive enums kept: `FfOpus`, `pt_OPUS`, SDP `opus` detection, and
  `FileFormatToString`/`FileFormatGetExtension` opus branches remain so configs
  mentioning opus don't crash — they just record as PCM WAV and skip Opus RTP decode.

### 2. Restored G.729 via the system package

Ubuntu 26.04 ships `libbcg729-dev` (1.1.1), so no manual build is needed:

```bash
sudo apt-get install -y libbcg729-dev
```

`orkaudio/filters/g729codec/Makefile.am` was adjusted:
- `LIBADD`: dropped `-L/opt/bcg729 -L/usr/lib` (system linker path is used)
- `INCLUDES`: dropped `-I/opt/bcg729/include -I/opt/bcg729/src`

The resulting `libg729codec.so` links against `/usr/lib/x86_64-linux-gnu/libbcg729.so.0`.

### 3. Boost 1.90 compatibility fixes

The code was written against Boost 1.63. Ubuntu 26.04 ships Boost 1.90, which
removed several deprecated APIs:

| File | Old API (<=1.63) | New API (1.70+) |
|---|---|---|
| `orkbasecxx/SslUtils.h` | `boost::asio::io_service` | `boost::asio::io_context` |
| `orkbasecxx/SslUtils.cpp` | `resolver::query` + `resolver::iterator`; connect handler takes `iterator` | `resolver.resolve(host, service)` returning `results_type`; connect handler takes `endpoint` |
| `orkbasecxx/configure.in` | `boost_lib=-lboost_system` | `boost_lib=` (boost_system is header-only since 1.69, no `.so` exists) |

### 4. Dependencies not mentioned in BUILD_C++.txt

These are actually required but missing from the original doc:

- `libdw-dev` — orkaudio `configure` hard-fails without it (`AC_SEARCH_LIBS([dwarf_begin],[dw])`)
- `libunwind-dev` — same (`AC_SEARCH_LIBS([unw_backtrace],[unwind])`)
- `libcap-dev` — `orkbasecxx/Utils.cpp` includes `<sys/capability.h>` for the
  `check_pcap_capabilities()` startup check (verifies CAP_NET_RAW/CAP_NET_ADMIN)
- `libbackward-cpp-dev` — `orkaudio/OrkAudio.cpp` includes `backward.hpp` for
  crash-time stack traces
- `libbcg729-dev` — for the G.729 codec plugin (system package, replaces /opt build)

### 5. Supported codecs after these changes

GSM, iLBC, G.722, Speex, G.726, G.729, PCMU/PCMA — covers common VoIP deployments.
SILK (Skype) and Opus RTP streams are detected but not decoded; their audio is
skipped gracefully.

## Runtime notes

- `orkaudio` needs to find `liborkbase.so`: either `sudo make install` inside
  `orkbasecxx` (installs to `/usr/lib`), or run with
  `LD_LIBRARY_PATH=<repo>/orkbasecxx/.libs`.
- Packet capture needs `CAP_NET_RAW` + `CAP_NET_ADMIN`. Either run as root or:
  `sudo setcap cap_net_raw,cap_net_admin+ep /path/to/orkaudio`
- Config templates: `orkaudio/config-linux-template.xml` and
  `orkaudio/logging-linux-template.properties`.
