import { loginAll } from "./auth";
import { createTelegramBot } from "./telegramBot";
import { ENABLE_TELEGRAM_BOT, ENABLE_互动答题_CHECK } from "./config";
import { createWebsocketServer } from "./websocketServer";
import { register互动答题Watchers } from "./interactiveQuiz";
import { register签到EventHandlers, register签到Watchers } from "./checkin";
import { postLaunch } from "./postLaunch";
import { preLaunch } from "./preLaunch";
import { LabelledLogger } from "./logger";

const logger = new LabelledLogger("main");

// 课堂派混用了 class 与 course 两个词，我们统一用 class (class_)
// 此外，课堂派的 API 中大量使用“attence”这个拼写错误的词指代“签到”，我们将直接使用汉字，互动答题同理

async function bootstrap() {
  await loginAll();
  if (ENABLE_TELEGRAM_BOT) await createTelegramBot();
  postLaunch();
}

async function main() {
  preLaunch();

  register签到EventHandlers();
  register签到Watchers();
  if (ENABLE_互动答题_CHECK) register互动答题Watchers();

  // 先监听端口，避免登录重试期间客户端/配置页出现「拒绝连接」
  await createWebsocketServer();

  bootstrap().catch((err) => {
    logger.error(`Background bootstrap failed: ${err}`);
  });
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
