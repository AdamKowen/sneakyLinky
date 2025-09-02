const cron = require('node-cron');
const logger = require('./utils/logger');
const { createDomainHotsetVersion } = require('./services/domainHotsetService');

const WEEKLY_SCHEDULE = process.env.HOTSET_CRON_SCHEDULE || '*/1 * * * *';
const TIMEZONE = process.env.SCHEDULER_TIMEZONE || 'Asia/Jerusalem';

function startWeeklyHotsetScheduler() {
  const task = cron.schedule(
    WEEKLY_SCHEDULE,
    async () => {
      logger.info('[SCHEDULER] Weekly hotset job started');
      try {
        const rec = await createDomainHotsetVersion();
        logger.info(`[SCHEDULER] Hotset created successfully. Version=${rec?.version ?? 'N/A'}`);
      } catch (err) {
        logger.error(`[SCHEDULER] Hotset creation failed: ${err.message}`);
      }
    },
    { scheduled: false, timezone: TIMEZONE }
  );

  task.start();
  logger.info(`[SCHEDULER] Started with pattern "${WEEKLY_SCHEDULE}" (${TIMEZONE})`);
  return task;
}

function stopWeeklyHotsetScheduler(task) {
  if (task) task.stop();
}

module.exports = { startWeeklyHotsetScheduler, stopWeeklyHotsetScheduler };