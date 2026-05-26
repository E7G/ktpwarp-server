import { USERS } from "./config";
import { HEADERS } from "./constants";
import { CredentialType } from "./types";
import { fancyFetch, getReqtimestamp } from "./util";
import { LabelledLogger } from "./logger";

const logger = new LabelledLogger("auth");

export let credentials: CredentialType[] = [];

export async function login(username: string, password: string) {
  const _response = await fancyFetch("https://openapiv5.ketangpai.com/UserApi/login", {
    method: "POST",
    headers: HEADERS,
    body: {
      email: username,
      password,
      remember: "1",
      source_type: 1,
      reqtimestamp: getReqtimestamp(),
    },
  });
  const response: any = await _response.json();

  return response.data.token;
}

export async function loginAll() {
  if (USERS.length === 0) {
    throw new Error("No users configured in config.json (USERS is empty)");
  }

  logger.info("Logging in...");

  const maxAttempts = 20;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const tokens = await Promise.all(USERS.map((user) => login(user.username, user.password)));
    credentials = tokens.map((token, index) => ({ friendlyName: USERS[index].friendlyName, token }));

    const failed = credentials.filter((c) => typeof c.token === "undefined");
    if (failed.length === 0) {
      logger.info(`${credentials.length} user(s) logged in`);
      return;
    }

    logger.warn(
      `Login failed for ${failed.length}/${credentials.length} user(s), retry ${attempt}/${maxAttempts} in 15s...`,
    );
    await new Promise((resolve) => setTimeout(resolve, 15_000));
  }

  throw new Error("Login failed after maximum retries; check USERS in config.json");
}
