import { CONFIG_VERSION } from "./config";
import { CURRENT_CONFIG_VERSION } from "./constants";
import { LabelledLogger, setupLogger } from "./logger";

const bootLogger = new LabelledLogger("boot");

export function preLaunch() {
  setupLogger();
  
  if (CONFIG_VERSION !== CURRENT_CONFIG_VERSION) {
    bootLogger.warn(
      `config.json version is ${CONFIG_VERSION}, expected ${CURRENT_CONFIG_VERSION}. Service will still start; fix config when convenient.`,
    );
  }
}
