
all: update_hackedupreader_0.1.1_install.bin update_hackedupreader_0.1.1_uninstall.bin

update_hackedupreader_0.1.1_install.bin:
	kindletool create ota2 -d k5w -d k5g install $@

update_hackedupreader_0.1.1_uninstall.bin:
	kindletool create ota2 -d k5w -d k5g uninstall $@

clean:
	rm -f *.bin
