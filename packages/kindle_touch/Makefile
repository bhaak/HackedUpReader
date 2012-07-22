VERSION=0.1.1

all: hackedupreader_${VERSION}_install.bin hackedupreader_${VERSION}_uninstall.bin zip
hackedupreader_${VERSION}_install.bin:
	kindletool create ota2 -d k5w -d k5g install $@

hackedupreader_${VERSION}_uninstall.bin:
	kindletool create ota2 -d k5w -d k5g uninstall $@

clean:
	rm -rf *.bin *.zip hackedupreader_${VERSION}

zip:
	mkdir hackedupreader_${VERSION}
	mv hackedupreader_${VERSION}_install.bin hackedupreader_${VERSION}_uninstall.bin hackedupreader_${VERSION}
	zip -r0 hackedupreader_${VERSION}.zip hackedupreader_${VERSION}/
