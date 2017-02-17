package com.ctrip.xpipe.redis.keeper.handler;

import java.util.Set;

import com.ctrip.xpipe.redis.core.protocal.RedisProtocol;
import com.ctrip.xpipe.redis.core.protocal.protocal.BulkStringParser;
import com.ctrip.xpipe.redis.keeper.KeeperRepl;
import com.ctrip.xpipe.redis.keeper.RedisClient;
import com.ctrip.xpipe.redis.keeper.RedisKeeperServer;
import com.ctrip.xpipe.redis.keeper.RedisMaster;
import com.ctrip.xpipe.redis.keeper.RedisSlave;
import com.ctrip.xpipe.utils.StringUtil;

/**
 * @author wenchao.meng
 *
 * 2016年4月22日 下午3:51:33
 */
public class InfoHandler extends AbstractCommandHandler{

	@Override
	public String[] getCommands() {
		return new String[]{"info"};
	}

	@Override
	public boolean isLog(String[] args) {
		// INFO command is called by sentinel very frequently, so we need to hide the log
	    return false;
	}

	@Override
	protected void doHandle(String[] args, RedisClient redisClient) {
		logger.debug("[doHandle]{},{}", redisClient, StringUtil.join(" ", args));

		boolean isDefault = false;
		boolean isAll = false;
		String section = null;

		if(args.length == 0){

			isDefault = true;
		}

		if(args.length == 1){

			if(args[0].equalsIgnoreCase("all")){
				isAll = true;
			}else{
				section = args[0];
			}
		}

		StringBuilder sb = new StringBuilder();
		RedisKeeperServer redisKeeperServer = redisClient.getRedisKeeperServer();

		server(isDefault, isAll, section, sb, redisKeeperServer);
		replication(isDefault, isAll, section, sb, redisKeeperServer);

		redisClient.sendMessage(new BulkStringParser(sb.toString()).format());
	}

	private void server(boolean isDefault, boolean isAll, String section, StringBuilder sb,
			RedisKeeperServer redisKeeperServer) {

		if(isDefault || isAll || "server".equalsIgnoreCase(section)){

			sb.append("# Server" + RedisProtocol.CRLF);
			sb.append(redisKeeperServer.info() + RedisProtocol.CRLF);

		}

	}

	private void replication(boolean isDefault, boolean isAll, String section, StringBuilder sb, RedisKeeperServer redisKeeperServer) {

		if(isDefault || isAll || "replication".equalsIgnoreCase(section)){

			sb.append("# Replication" + RedisProtocol.CRLF);
			sb.append("role:" + redisKeeperServer.role() + RedisProtocol.CRLF);
			sb.append("state:" + redisKeeperServer.getRedisKeeperServerState().keeperState() + RedisProtocol.CRLF);
			RedisMaster redisMaster =  redisKeeperServer.getRedisMaster();
			String masterHost = redisMaster == null ? null: redisMaster.masterEndPoint().getHost();
			Integer masterPort = redisMaster == null ? null: redisMaster.masterEndPoint().getPort();
			if(masterHost != null){
				sb.append("master_host:" + masterHost + RedisProtocol.CRLF );
				sb.append("master_port:"  + masterPort +  RedisProtocol.CRLF );
				/**
				 * If not report master link status as up, then sentinal is found crashed
				 * when sentinel is doing slaveof new_master_ip new_master_port
				 */
				sb.append("master_link_status:up" +  RedisProtocol.CRLF );
			}
			Set<RedisSlave> slaves = redisKeeperServer.slaves();
			sb.append("connected_slaves:" + slaves.size() + RedisProtocol.CRLF);
			int slaveIndex = 0;
			for(RedisSlave slave : slaves){
				sb.append(String.format("slave%d:%s" + RedisProtocol.CRLF, slaveIndex, slave.info()));
				slaveIndex++;
			}
			/**
			 * To make sure keeper is the least option to be the new master when master is down
             */
			sb.append("slave_repl_offset:0" + RedisProtocol.CRLF);
			sb.append("slave_priority:0" + RedisProtocol.CRLF);

			KeeperRepl keeperRepl = redisKeeperServer.getKeeperRepl();

			long beginOffset = keeperRepl.getBeginOffset();
			sb.append("repl_backlog_active:1" + RedisProtocol.CRLF);
			sb.append("repl_backlog_first_byte_offset:" + beginOffset+ RedisProtocol.CRLF);
            try {
				long endOffset = keeperRepl.getEndOffset();
                sb.append("master_repl_offset:" + endOffset + RedisProtocol.CRLF);
                sb.append("repl_backlog_size:" + (endOffset - beginOffset + 1) + RedisProtocol.CRLF);
                sb.append("repl_backlog_histlen:" + (endOffset - beginOffset + 1)+ RedisProtocol.CRLF);
			} catch (Throwable ex) {
				sb.append("error_message:" + ex.getMessage() + RedisProtocol.CRLF);
				logger.info("Cannot calculate end offset", ex);
			}
		}
	}

}
