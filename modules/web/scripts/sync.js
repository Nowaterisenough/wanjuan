import fs from "node:fs";
import { URL } from "node:url";

const WANJUAN_ASSETS_WEB_VUE_DIR = new URL(
  "../../../app/src/main/assets/web/vue/",
  import.meta.url,
);
const VUE_DIST_DIR = new URL("../dist/", import.meta.url);

console.log("> delete", WANJUAN_ASSETS_WEB_VUE_DIR.pathname);
fs.rmSync(WANJUAN_ASSETS_WEB_VUE_DIR, {
  force: true,
  recursive: true,
});

console.log("> mkdir", WANJUAN_ASSETS_WEB_VUE_DIR.pathname);
fs.mkdirSync(WANJUAN_ASSETS_WEB_VUE_DIR, {
  recursive: true,
});

console.log("> cp dist files");
fs.cpSync(VUE_DIST_DIR, WANJUAN_ASSETS_WEB_VUE_DIR, {
  recursive: true,
});

console.log("> cp success");
