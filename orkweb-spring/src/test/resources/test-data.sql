-- Oreka 测试数据(开发/手工验证 API 用)
-- 用法:docker exec -i oreka-mysql mysql -uroot -poreka123 oreka < test-data.sql
-- 注意:此脚本幂等性有限,重复执行会产生重复 tape(用 nativeCallId 判断前先清表)
-- Direction 枚举(Hibernate ordinal):IN=0, OUT=1, ALL=2, UNKN=3

-- 如需重置,取消下行注释:
-- DELETE FROM orksegment; DELETE FROM orktape; DELETE FROM orksession;

INSERT INTO orksession (timestamp, duration) VALUES
  ('2026-10-16 10:00:00', 300),
  ('2026-10-16 14:30:00', 45),
  ('2026-10-15 09:15:00', 600);

INSERT INTO orktape (timestamp, duration, filename, localParty, remoteParty, direction, portName, nativeCallId, state) VALUES
  ('2026-10-16 10:00:00', 300, '20261016_100000_1001_1002.wav', '1001', '1002', 0, 'SIP/1001', 'call-uuid-001', 'STOP'),
  ('2026-10-16 14:30:00', 45, '20261016_143000_1001_95558.wav', '1001', '95558', 1, 'SIP/1001', 'call-uuid-002', 'STOP'),
  ('2026-10-15 09:15:00', 600, '20261015_091500_1003_1004.wav', '1003', '1004', 0, 'SIP/1003', 'call-uuid-003', 'STOP');

INSERT INTO orksegment (timestamp, duration, localParty, localEntryPoint, remoteParty, direction, loginString, portName, sessionOffset, tapeOffset, session_id, tape_id, user_id) VALUES
  ('2026-10-16 10:00:00', 300, '1001', '9000', '1002', 0, '1001', 'SIP/1001', 0, 0, 1, 1, 1),
  ('2026-10-16 14:30:00', 45, '1001', '9000', '95558', 1, '1001', 'SIP/1001', 0, 0, 2, 2, 1),
  ('2026-10-15 09:15:00', 600, '1003', '9000', '1004', 0, '1003', 'SIP/1003', 0, 0, 3, 3, 1);
