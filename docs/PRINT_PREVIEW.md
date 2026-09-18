# Current print preview

Meter and Printer show the current job's model thumbnail and reported print name. Tap the image for an enlarged view. This is the slicer's rendered thumbnail, not an interactive 3D mesh or a live representation of completed layers.

The app reads the exact MQTT `gcode_file` from the selected printer using implicit FTPS on port 990 and that printer's saved LAN access code. It tries the reported path, then the same filename in `/cache` and the root. It does not choose a similarly named or newest file, upload files, or use cloud credentials.

The preview is `Metadata/plate_N.png` from the 3MF ZIP. When an explicit plate is reported in the print command's `param`, that plate is selected. Otherwise only archives containing exactly one standard plate thumbnail are accepted. Missing files, ambiguous plates, LAN file permission failures, or unsupported plain G-code jobs display an unavailable state with Retry. The job name remains visible.

Downloads have a 64 MB archive limit, 60-second transfer deadline and socket timeouts. Temporary archives are deleted after extraction. Thumbnail entries are limited to 4 MB, decoded dimensions are bounded, and four thumbnails are cached in memory using printer/job/file/plate identity. A new job clears old file metadata if its filename has not arrived yet.

Validated against a live P1S job using the Pixel 9 Pro Fold: actual FTPS download, extraction/decoding, Meter display, and enlarged preview. Unit tests cover exact path selection, unsafe path rejection, and multi-plate selection. Firmware that does not permit LAN file access cannot provide this preview through this implementation.

References: [BambuStudio 3MF format](https://github.com/bambulab/BambuStudio/blob/master/src/libslic3r/Format/bbs_3mf.cpp), [OpenBambuAPI FTPS](https://github.com/Doridian/OpenBambuAPI/blob/main/ftp.md).
