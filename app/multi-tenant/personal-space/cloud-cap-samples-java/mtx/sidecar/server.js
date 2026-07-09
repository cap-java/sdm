const cds = require('@sap/cds');

const LOG = cds.log('server');

cds.once('bootstrap', async (app) => {
  const databaseId = process.env.DATABASE_ID;
  if (databaseId && databaseId !== 'REPLACE_WITH_YOUR_DATABASE_ID') {
    cds.env.requires['cds.xt.DeploymentService'] ??= {};
    cds.env.requires['cds.xt.DeploymentService'].hdi ??= {};
    cds.env.requires['cds.xt.DeploymentService'].hdi.create ??= {};
    cds.env.requires['cds.xt.DeploymentService'].hdi.create.database_id = databaseId;
    LOG.info(`Configured HDI database_id from environment`);
  } else {
    LOG.error(`DATABASE_ID environment variable is not set or is still the placeholder. HDI container creation will fail.`);
  }
});

module.exports = cds.server;
