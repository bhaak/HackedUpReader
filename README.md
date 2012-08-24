HackedUpReader for Kindle Touch
===============================

HackedUpReader is a CoolReader 3 port for the Kindle Touch using the X11
gui of the CoolReader project, minimally adapted for the touch interface.

It is only meant to act as a temporary solution to get a ePub reader for
the Kindle Touch.

HackedUpReader is by no means thought for general usage. Currently you
need to be familiar with accessing your Kindle Touch and starting
programs from the command line to use it.

What works:
 - reading e-books
 - opening other e-books
 - setting options
 - battery display
 - searching
 - bookmarking
 - screen refresh for removing e-ink artifacts
   * automatic after a configurable amount of pages

What doesn't work:
 - dictionary support


Installation and running
------------------------
You can download either download the zip or tar.gz file. The only
difference is that the zip file also contains a GUI launcher
extension.

The zip file contains standard Kindle update files. Put the
hackedupreader_x.x.x_install.bin into your user data directory,
select Menu -> Settings -> Menu -> Update Your Kindle for installing
it.


The tar.gz only contains HackedUpReader. Extract the tar.gz file into
your Kindle Touch user data directory.

ssh into your Kindle Touch and start it with:

    /mnt/us/hackedupreader/bin/cr3 /mnt/us/path/to/your/ebook.epub &

Because of some hard coded paths HackedUpReader must be in
the /mnt/us/hackedupreader directory.


For display quality and preventing crashes when using non-standard fonts install this version of the freetype library: http://www.mobileread.com/forums/showthread.php?p=1998991#post1998991


There are 2 different GUI launcher extensions:
 - https://github.com/bhaak/HackedUpReaderExtension (builds a Launcher sub menu out of your documents folder for starting HackedUpReader)
 - https://github.com/varnie/HackedUpReaderLauncher (directly start HackedUpReader)


Controls
--------
When in the main screen, touching in the lower half of the screen
opens the next page, touching in the upper half opens the previous
page (customizable as of 0.2.0 in the settings "Page turning").
Touching the top bar will open the menu.

When in the menu, touching the top bar acts as 'Ok', touching the
bottom bar on the left side will open the previous menu page,
touching the right side will open the next menu page if there are any.

Touching numbered sections in the menu will select them or open any
existing sub menu.


Known Bugs
----------
Pressing the home button when a document is being shown in the standard
Kindle reader, the home screen will be shown without any possibility to
return to HackedUpReader. Current workaround is to restart the kindle.


Compilation
------------
The source code can be found at https://github.com/bhaak/HackedUpReader

You need to set the correct values for your cross-compilation
environment in tools/toolchain-arm-linux-gnueabi.cmake for the cmake
variables CMAKE_C_COMPILER, CMAKE_CXX_COMPILER, CMAKE_STRIP and
CMAKE_FIND_ROOT_PATH.

In the following command, "/kindle_development" should be the same
directory as CMAKE_FIND_ROOT_PATH.

    $ mkdir build; cd build
    $ env PKG_CONFIG_LIBDIR=/kindle_development_dir/lib/pkgconfig \
       cmake \
        -DCMAKE_PREFIX_PATH=/kindle_development_dir \
        -DCMAKE_INSTALL_PREFIX=/mnt/us/hackedupreader -DCMAKE_BUILD_TYPE=Release -DGUI=CRGUI_XCB \
        -DCMAKE_TOOLCHAIN_FILE=`pwd`/../tools/toolchain-arm-linux-gnueabi.cmake ..
    $ make && make install


Who are you and why are you doing this?
---------------------------------------
I'm Patric Mueller <bhaak@gmx.net> and I'm doing this because - contrary
to Amazon who's manufacturing a great piece of hardware but is equipping
it with inferior software - I like my e-books kerned, hyphenated and
justified.

Contributing author: Sergey Alekseyev <varnie29a@mail.ru>
