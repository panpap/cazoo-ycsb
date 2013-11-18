require 'thread'
begin
  	require 'zk'
  	rescue LoadError
	puts "Some packages/gems may needs to be installed..."
	system('sudo aptitude install ruby1.9.1-dev')
	system('sudo gem install slyphon-zookeeper')
	system('sudo gem install zk')
end

class NotifyRingChanges
	def initialize
		connTo="localhost:2181" #default
		if ARGV.size<1
			STDERR.puts "WARNING: Using default ("+connTo+") Zookeper Server address\nTo change it: ruby ringNotifier.rb <zkServerHost:port>\n\n"
		else
			connTo=ARGV[0]
			STDERR.puts "Connecting to #{connTo}\n\n"
		end
		@zk = ZK.new(connTo)
		@queue = Queue.new
		@path = "/cazooMaster"#'/cassRing'
	end

	def dofunc(data,event)
		if data==nil #delete event
			puts event+"$"
			exit
		else 
			puts event+"$"+data.inspect
			output = open("pipe", "w+")
			output.puts event+"$"+data.inspect
			output.flush
		end
	end

	def run
		data = @zk.get(@path, watch: true).first
		dofunc(data,"")
		@sub = @zk.register(@path) do |event|
			if event.node_deleted?
				dofunc(nil,event.event_name)
				@queue.push(:deleted)
			elsif event.node_changed? or event.node_created?
				data = @zk.get(@path, watch: true).first    # fetch latest data and re-set watch
				dofunc(data,event.event_name)
				@queue.push(:got_event)
			end
		end
		#@zk.delete(@path) rescue ZK::Exceptions::NoNode
		@zk.stat(@path, watch: true)
		#@zk.create(@path, 'Hello, pajnpap')
		@queue.pop
		#@zk.set(@path, 'Hello again')
		#@queue.pop
		#@zk.set(@path, "ooh, an update!")
		#@queue.pop
		loop do 
		  	sleep 10800 #three hour sleep slot for each loop
		end
		ensure
			@zk.close!
		end
	end

NotifyRingChanges.new.run
STDERR.puts "Finished!"
