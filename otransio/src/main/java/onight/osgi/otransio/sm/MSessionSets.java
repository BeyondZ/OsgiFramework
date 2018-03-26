package onight.osgi.otransio.sm;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.StringUtils;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.impl.FutureImpl;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.ck.CKConnPool;
import onight.osgi.otransio.impl.NodeInfo;
import onight.tfw.mservice.NodeHelper;
import onight.tfw.otransio.api.PackHeader;
import onight.tfw.otransio.api.PacketHelper;
import onight.tfw.otransio.api.beans.FramePacket;
import onight.tfw.otransio.api.session.LocalModuleSession;
import onight.tfw.otransio.api.session.PSession;
import onight.tfw.outils.serialize.UUIDGenerator;

@Data
@Slf4j
public class MSessionSets {

	String packIDKey = "";

	public MSessionSets() {
		packIDKey = UUIDGenerator.generate() + ".SID";
	}

	OutgoingSessionManager osm;

	HashMap<String, PSession> sessionByNodeName = new HashMap<>();

	HashMap<String, LocalModuleSession> localsessionByModule = new HashMap<>();

	ConcurrentHashMap<String, FutureImpl<FramePacket>> packMaps = new ConcurrentHashMap<>();
	AtomicLong recvCounter = new AtomicLong(0);
	AtomicLong sendCounter = new AtomicLong(0);
	AtomicLong duplCounter = new AtomicLong(0);
	AtomicLong dropCounter = new AtomicLong(0);
	AtomicLong sentCounter = new AtomicLong(0);
	// AtomicLong allRCounter = new AtomicLong(0);
	// AtomicLong allSCounter = new AtomicLong(0);
	// ConcurrentHashMap<String,HashSet<PSession>> connsByNodeID=new
	// ConcurrentHashMap<String, HashSet<PSession>>();

	RemoteModuleBean rmb = new RemoteModuleBean();

	public String getJsonInfo() {
		StringBuffer sb = new StringBuffer();
		sb.append("{");
		sb.append("\"name\":\"").append(rmb.getNodeInfo().getNodeName()).append("\"");
		sb.append(",\"addr\":\"").append(rmb.getNodeInfo().getAddr()).
		append(":").append(rmb.getNodeInfo().getPort()).append("\"");
		sb.append(",\"modules\":[");
		int i = 0;
		for (Entry<String, LocalModuleSession> kv : localsessionByModule.entrySet()) {
			if (kv.getKey().length() > 0) {
				if (i > 0)
					sb.append(",");
				i++;
				sb.append(kv.getValue().getJsonStr());
			}
		}

		sb.append("],\"sessions\":[");
		i = 0;
		for (Entry<String, PSession> kv : sessionByNodeName.entrySet()) {
			if (i > 0)
				sb.append(",");
			if (kv.getValue() instanceof RemoteModuleSession) {
				sb.append(((RemoteModuleSession) kv.getValue()).getJsonStr());
				i++;
			} else {
			}

		}
		sb.append("],\"stats\":{");
		sb.append("\"recv\":").append(recvCounter.get());
		sb.append(",\"send\":").append(sendCounter.get());
		sb.append(",\"sent\":").append(sentCounter.get());
		// sb.append(",\"allS\":").append(allSCounter.get());
		sb.append(",\"drop\":").append(dropCounter.get());
		sb.append(",\"dupl\":").append(duplCounter.get());
		sb.append("}");
//		sb.append(",\"osm\":").append(osm.getJsonInfo());
		sb.append("}");
		return sb.toString();
	}

	// public RemoteModuleSession byNodeIdx(Integer idx) {
	// if (sessionByNodeIdx.containsKey(idx)) {
	// return sessionByNodeIdx.get(idx);
	// }
	// return null;
	// }

	public PSession byNodeName(String name) {
		return sessionByNodeName.get(name);
	}

	public synchronized RemoteModuleSession addRemoteSession(NodeInfo node, CKConnPool ckpool) {
		PSession psession = sessionByNodeName.get(node.getNodeName());
		RemoteModuleSession session = null;
		if (psession == null) {
			session = new RemoteModuleSession(node, this);
			psession = session;
			sessionByNodeName.put(node.getNodeName(), psession);
			session.setConnsPool(ckpool);
			// osm.ck.addCheckHealth(ckpool);
		} //
		return session;

	}

	public FramePacket getLocalModulesPacket() {
		FramePacket ret = PacketHelper.genSyncPack(PackHeader.REMOTE_LOGIN, PackHeader.REMOTE_MODULE, rmb);
		log.debug("getLocalModulePack:" + ret.getFixHead().toStrHead() + ":" + rmb);
		return ret;
	}

	public FramePacket getLocalModulesPacketBack() {
		FramePacket ret = PacketHelper.genSyncPack(PackHeader.REMOTE_LOGIN_RET, PackHeader.REMOTE_MODULE, rmb);
		log.debug("getLocalModulePack.back:" + ret.getFixHead().toStrHead() + ":" + rmb);
		return ret;
	}

	public synchronized LocalModuleSession addLocalMoudle(String module) {
		// localSessionsByModule.put(session.getModule(), session);
		LocalModuleSession lms = localsessionByModule.get(module);
		if (lms == null) {
			lms = new LocalModuleSession(module);
			localsessionByModule.put(module, lms);
		}
		return lms;
	}

	public void dropSession(String name) {
		if (StringUtils.isNotBlank(name)) {
			PSession session = sessionByNodeName.remove(name);
			osm.rmNetPool(name);
			if (session != null) {
				dropCounter.incrementAndGet();
				if (session instanceof RemoteModuleSession)
					((RemoteModuleSession) session).destroy();
			}

		}
	}

	public synchronized void renameSession(String oldname, String newname) {
		if (StringUtils.isNotBlank(oldname) && StringUtils.isNotBlank(newname)) {
			PSession session = sessionByNodeName.get(oldname);
			if (session != null) {
				session.setMmid(newname);
				osm.nodePool.changePoolName(oldname, newname);
				if (session instanceof RemoteModuleSession) {
					RemoteModuleSession rms = (RemoteModuleSession) session;
					rms.nodeInfo.setNodeName(newname);
				}
				sessionByNodeName.put(newname, session);
				sessionByNodeName.remove(oldname);
			}
		}
	}

}
