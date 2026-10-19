# Icon fonts for builds without the Pro files

The apps draw their icons with Font Awesome 5 Pro (solid and regular). Its licence does not allow
publishing the font files, so they are not in this repo. The build copies them into the app
(`R.font.fa_solid`, `R.font.fa_regular`) from:

1. a checkout of the private `rpm-fonts-pro` repo next to this repo, when there is one; otherwise
2. this folder: Font Awesome Free 5.15.4, which has the same codepoints for the free icons. The
   icons that only exist in Pro are blank in such a build.

The fonts in this folder are under the SIL Open Font License 1.1 (see `LICENSE.txt`).
